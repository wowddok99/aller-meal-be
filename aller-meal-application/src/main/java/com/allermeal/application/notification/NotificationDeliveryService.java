package com.allermeal.application.notification;

import com.allermeal.application.port.out.EmailDecryptor;
import com.allermeal.application.port.out.NotificationMailSender;
import com.allermeal.application.port.out.NotificationRequestRepository;
import com.allermeal.application.port.out.NotificationMailContextRepository;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.command.NotificationMailCommand;
import com.allermeal.application.port.out.result.NotificationMailContextResult;
import com.allermeal.domain.notification.NotificationId;
import com.allermeal.domain.notification.NotificationRequest;
import com.allermeal.domain.notification.NotificationStatus;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class NotificationDeliveryService {
	private static final Logger LOGGER = Logger.getLogger(NotificationDeliveryService.class.getName());

	private final NotificationRequestRepository notificationRequestRepository;
	private final UserRepository userRepository;
	private final EmailDecryptor emailDecryptor;
	private final NotificationMailSender mailSender;
	private final Clock clock;
	private final Duration retryDelay;
	private final NotificationMailContextRepository mailContexts;
	private final NotificationMealSummaryService mealSummaries;

	public NotificationDeliveryService(
		NotificationRequestRepository notificationRequestRepository,
		UserRepository userRepository,
		EmailDecryptor emailDecryptor,
		NotificationMailSender mailSender,
		Clock clock,
		Duration retryDelay,
		NotificationMailContextRepository mailContexts,
		NotificationMealSummaryService mealSummaries
	) {
		this.notificationRequestRepository = Objects.requireNonNull(
			notificationRequestRepository, "NotificationRequestRepository는 null일 수 없습니다.");
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository는 null일 수 없습니다.");
		this.emailDecryptor = Objects.requireNonNull(emailDecryptor, "EmailDecryptor는 null일 수 없습니다.");
		this.mailSender = Objects.requireNonNull(mailSender, "NotificationMailSender는 null일 수 없습니다.");
		this.clock = Objects.requireNonNull(clock, "Clock은 null일 수 없습니다.");
		if (retryDelay == null || retryDelay.isNegative()) {
			throw new IllegalArgumentException("알림 재시도 지연 시간은 0 이상이어야 합니다.");
		}
		this.retryDelay = retryDelay;
		this.mailContexts = Objects.requireNonNull(mailContexts, "메일 대상 저장소는 null일 수 없습니다.");
		this.mealSummaries = Objects.requireNonNull(mealSummaries, "급식 요약 서비스는 null일 수 없습니다.");
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public NotificationDeliveryResult deliver(NotificationId notificationId) {
		Objects.requireNonNull(notificationId, "알림 ID는 null일 수 없습니다.");
		NotificationRequest request = notificationRequestRepository.findById(notificationId).orElse(null);
		if (request == null || !request.isActive()) {
			return new NotificationDeliveryResult(notificationId, NotificationStatus.CANCELED, 0);
		}
		User user = userRepository.findById(request.ownerId()).orElse(null);
		if (user == null || user.status() != UserStatus.ACTIVE) {
			NotificationRequest canceled = cancelForInactiveOwner(request, user);
			return new NotificationDeliveryResult(canceled.id(), canceled.status(), canceled.attemptCount());
		}
		// 자녀 삭제의 cascade와 같은 owner→child→target→request 순서로 잠근다.
		NotificationMailContextResult context = mailContexts.findForDelivery(request).orElse(null);
		NotificationRequest sending = request.startSending(clock.instant());
		NotificationRequest lockedSending = notificationRequestRepository
			.startSendingIfOwnerActive(request.status(), sending)
			.orElse(null);
		if (lockedSending == null) {
			return new NotificationDeliveryResult(request.id(), NotificationStatus.CANCELED, request.attemptCount());
		}
		if (lockedSending.status() == NotificationStatus.CANCELED) {
			return new NotificationDeliveryResult(lockedSending.id(), lockedSending.status(), lockedSending.attemptCount());
		}
		if (context == null || !matchesTarget(lockedSending, context)) {
			NotificationRequest canceled = lockedSending.cancelForInvalidContext(clock.instant());
			notificationRequestRepository.save(NotificationStatus.SENDING, canceled);
			return new NotificationDeliveryResult(canceled.id(), canceled.status(), canceled.attemptCount());
		}
		NotificationMailCommand command;
		try {
			command = mailCommand(lockedSending, user, context);
		} catch (RuntimeException exception) {
			return recordFailure(lockedSending, "NOTIFICATION_PREPARATION_FAILED", "알림 내용 준비에 실패했습니다.", exception);
		}
		try {
			mailSender.send(command);
		} catch (RuntimeException exception) {
			return recordFailure(lockedSending, "SMTP_SEND_FAILED", "알림 메일 발송에 실패했습니다.", exception);
		}
		NotificationRequest sent = lockedSending.markSent(clock.instant());
		notificationRequestRepository.save(NotificationStatus.SENDING, sent);
		return new NotificationDeliveryResult(sent.id(), sent.status(), sent.attemptCount());
	}

	private NotificationRequest cancelForInactiveOwner(NotificationRequest request, User user) {
		if (user != null && user.status() == UserStatus.SUSPENDED) {
			notificationRequestRepository.cancelPendingAndRetryForSuspendedOwner(user.id(), clock.instant());
			NotificationRequest current = notificationRequestRepository.findById(request.id()).orElse(request);
			if (!current.isActive()) {
				return current;
			}
			NotificationRequest canceled = current.cancelForOwnerAccessRestriction(clock.instant());
			saveInactiveOwnerCancellation(current, canceled);
			return canceled;
		}
		NotificationRequest canceled = request.cancelForPersonalDataMasking(clock.instant());
		saveInactiveOwnerCancellation(request, canceled);
		return canceled;
	}

	private void saveInactiveOwnerCancellation(NotificationRequest request, NotificationRequest canceled) {
		try {
			notificationRequestRepository.save(request.status(), canceled);
		} catch (IllegalStateException exception) {
			NotificationRequest current = notificationRequestRepository.findById(request.id()).orElse(null);
			if (current == null || !current.isActive()) {
				return;
			}
			throw exception;
		}
	}

	private boolean matchesTarget(NotificationRequest request, NotificationMailContextResult context) {
		var target = context.target();
		return request.notificationTargetId().equals(target.notificationTargetId())
			&& request.childProfileId().equals(target.childProfileId())
			&& request.ownerId().equals(target.ownerId())
			&& request.notificationDate().equals(target.notificationDate())
			&& request.reason() == target.reason()
			&& request.contentVersion().equals(NotificationRequestKeyFactory.contentVersion(target));
	}

	private NotificationMailCommand mailCommand(NotificationRequest request, User user, NotificationMailContextResult context) {
		String recipientEmail = emailDecryptor.decrypt(user.encryptedEmail());
		return new NotificationMailCommand(request.id(), recipientEmail, request.childProfileId(),
			context.childName(), context.schoolName(), request.notificationDate(), request.reason(), mealSummaries.summarize(context));
	}

	private NotificationDeliveryResult recordFailure(NotificationRequest request, String code, String message, RuntimeException exception) {
		LOGGER.log(Level.SEVERE, "알림 처리 실패: " + code + ", notificationId=" + request.id(), exception);
		NotificationRequest failed = request.markFailed(code, message, clock.instant(), retryDelay);
		notificationRequestRepository.save(NotificationStatus.SENDING, failed);
		return new NotificationDeliveryResult(failed.id(), failed.status(), failed.attemptCount());
	}
}
