package com.allermeal.application.notification;

import com.allermeal.application.port.out.NotificationRequestRepository;
import com.allermeal.application.port.out.OutboxEventRepository;
import com.allermeal.application.port.out.result.NotificationRequestSaveResult;
import com.allermeal.application.port.out.result.PendingNotificationTargetResult;
import com.allermeal.domain.notification.NotificationChannel;
import com.allermeal.domain.notification.NotificationId;
import com.allermeal.domain.notification.NotificationRequest;
import com.allermeal.domain.outbox.OutboxEvent;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class NotificationRequestCreationService {

	private static final int DEFAULT_MAX_ATTEMPTS = 3;

	private final NotificationRequestRepository notificationRequestRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final Clock clock;

	public NotificationRequestCreationService(
		NotificationRequestRepository notificationRequestRepository,
		OutboxEventRepository outboxEventRepository,
		Clock clock
	) {
		this.notificationRequestRepository = notificationRequestRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.clock = clock;
	}

	@Transactional
	public NotificationRequestCreationResult createPendingEmailRequests(int limit) {
		if (limit < 1 || limit > 500) {
			throw new IllegalArgumentException("알림 요청 생성 limit은 1 이상 500 이하여야 합니다.");
		}
		List<PendingNotificationTargetResult> targets = notificationRequestRepository.findPendingTargets(limit);
		int created = 0;
		int duplicates = 0;
		int corrections = 0;
		int canceled = 0;
		for (PendingNotificationTargetResult target : targets) {
			NotificationRequest request = createRequest(target);
			NotificationRequestSaveResult result = notificationRequestRepository.saveCorrectionAware(request);
			if (result.created()) {
				created++;
				if (result.correction()) {
					corrections++;
				}
				canceled += result.canceledSupersededCount();
				outboxEventRepository.save(OutboxEvent.pending(
					UUID.randomUUID(),
					NotificationRequestEvents.NOTIFICATION_REQUESTED,
					NotificationRequestEvents.notificationRequestedPayload(result.notificationRequest()),
					clock.instant()));
			} else {
				duplicates++;
			}
		}
		return new NotificationRequestCreationResult(targets.size(), created, duplicates, corrections, canceled);
	}

	private NotificationRequest createRequest(PendingNotificationTargetResult target) {
		NotificationChannel channel = NotificationChannel.EMAIL;
		String correctionKey = NotificationRequestKeyFactory.correctionKey(target, channel);
		String contentVersion = NotificationRequestKeyFactory.contentVersion(target);
		String dedupKey = NotificationRequestKeyFactory.dedupKey(target, channel);
		return NotificationRequest.pending(
			new NotificationId(UUID.randomUUID()),
			target.notificationTargetId(),
			target.childProfileId(),
			target.ownerId(),
			target.notificationDate(),
			channel,
			target.reason(),
			dedupKey,
			correctionKey,
			contentVersion,
			false,
			null,
			DEFAULT_MAX_ATTEMPTS,
			clock.instant());
	}
}
