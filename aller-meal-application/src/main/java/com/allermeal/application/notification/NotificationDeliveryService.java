package com.allermeal.application.notification;

import com.allermeal.application.port.out.EmailDecryptor;
import com.allermeal.application.port.out.NotificationMailSender;
import com.allermeal.application.port.out.NotificationRequestRepository;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.command.NotificationMailCommand;
import com.allermeal.domain.notification.NotificationId;
import com.allermeal.domain.notification.NotificationRequest;
import com.allermeal.domain.notification.NotificationStatus;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class NotificationDeliveryService {

	private final NotificationRequestRepository notificationRequestRepository;
	private final UserRepository userRepository;
	private final EmailDecryptor emailDecryptor;
	private final NotificationMailSender mailSender;
	private final Clock clock;
	private final Duration retryDelay;

	public NotificationDeliveryService(
		NotificationRequestRepository notificationRequestRepository,
		UserRepository userRepository,
		EmailDecryptor emailDecryptor,
		NotificationMailSender mailSender,
		Clock clock,
		Duration retryDelay
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
			NotificationRequest canceled = request.cancelForPersonalDataMasking(clock.instant());
			savePersonalDataMaskedCancellation(request, canceled);
			return new NotificationDeliveryResult(canceled.id(), canceled.status(), canceled.attemptCount());
		}
		NotificationRequest sending = request.startSending(clock.instant());
		NotificationRequest lockedSending = notificationRequestRepository
			.startSendingIfOwnerActive(request.status(), sending)
			.orElse(null);
		if (lockedSending == null) {
			return new NotificationDeliveryResult(request.id(), NotificationStatus.CANCELED, request.attemptCount());
		}
		try {
			mailSender.send(mailCommand(lockedSending, user));
			NotificationRequest sent = lockedSending.markSent(clock.instant());
			notificationRequestRepository.save(NotificationStatus.SENDING, sent);
			return new NotificationDeliveryResult(sent.id(), sent.status(), sent.attemptCount());
		} catch (RuntimeException exception) {
			NotificationRequest failed = lockedSending.markFailed(
				"SMTP_SEND_FAILED", sanitizeFailureMessage(exception), clock.instant(), retryDelay);
			notificationRequestRepository.save(NotificationStatus.SENDING, failed);
			return new NotificationDeliveryResult(failed.id(), failed.status(), failed.attemptCount());
		}
	}

	private void savePersonalDataMaskedCancellation(NotificationRequest request, NotificationRequest canceled) {
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

	private NotificationMailCommand mailCommand(NotificationRequest request, User user) {
		String recipientEmail = emailDecryptor.decrypt(user.encryptedEmail());
		String subject = switch (request.reason()) {
			case RISK_DETECTED -> "Aller Meal 급식 알레르기 위험 알림";
			case NO_RISK -> "Aller Meal 오늘 급식은 등록 알레르기 위험이 없습니다";
			case NO_MEAL -> "Aller Meal 오늘 급식 정보가 없습니다";
			case RISK_PENDING -> "Aller Meal 급식 확인이 아직 진행 중입니다";
			case RISK_UNKNOWN, RISK_LABELING_FAILED -> "Aller Meal 급식 알레르기 확인이 필요합니다";
		};
		String body = """
			안녕하세요.

			자녀 급식 알림입니다.
			날짜: %s
			알림 사유: %s
			알림 ID: %s

			Aller Meal
			""".formatted(request.notificationDate(), request.reason(), request.id().value());
		return new NotificationMailCommand(request.id(), recipientEmail, subject, body);
	}

	private String sanitizeFailureMessage(RuntimeException exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return exception.getClass().getSimpleName();
		}
		return message.length() > 1000 ? message.substring(0, 1000) : message;
	}
}
