package com.allermeal.application.admin;

import com.allermeal.application.port.out.AdminAuditLogRepository;
import com.allermeal.application.port.out.AdminNotificationReprocessRequestRepository;
import com.allermeal.application.port.out.DeadLetterEventRepository;
import com.allermeal.application.port.out.NotificationRequestRepository;
import com.allermeal.application.port.out.OutboxEventRepository;
import com.allermeal.application.port.out.command.AdminAuditLogCommand;
import com.allermeal.application.port.out.command.AdminNotificationReprocessRequestCommand;
import com.allermeal.application.port.out.result.AdminNotificationReprocessRequestResult;
import com.allermeal.domain.outbox.OutboxEvent;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class AdminNotificationFailureService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 200;

	private final NotificationRequestRepository notificationRequestRepository;
	private final DeadLetterEventRepository deadLetterEventRepository;
	private final AdminNotificationReprocessRequestRepository reprocessRequestRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final AdminAuditLogRepository auditLogRepository;
	private final Clock clock;

	public AdminNotificationFailureService(
		NotificationRequestRepository notificationRequestRepository,
		DeadLetterEventRepository deadLetterEventRepository,
		AdminNotificationReprocessRequestRepository reprocessRequestRepository,
		OutboxEventRepository outboxEventRepository,
		AdminAuditLogRepository auditLogRepository,
		Clock clock
	) {
		this.notificationRequestRepository = notificationRequestRepository;
		this.deadLetterEventRepository = deadLetterEventRepository;
		this.reprocessRequestRepository = reprocessRequestRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.auditLogRepository = auditLogRepository;
		this.clock = clock;
	}

	public AdminFailedNotificationPageResult findFailedNotifications(User actor, int page, int pageSize) {
		requireAdmin(actor);
		validatePage(page, pageSize);
		return notificationRequestRepository.findFailed(page, pageSize);
	}

	public AdminDeadLetterEventPageResult findDeadLetterEvents(User actor, int page, int pageSize) {
		requireAdmin(actor);
		validatePage(page, pageSize);
		return deadLetterEventRepository.findRecent(page, pageSize);
	}

	@Transactional
	public AdminNotificationReprocessResult reprocessDeadLetterEvent(
		User actor,
		UUID deadLetterEventId,
		String idempotencyKey
	) {
		requireAdmin(actor);
		if (deadLetterEventId == null) {
			throw new AdminInvalidNotificationFailureRequestException();
		}
		String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
		AdminDeadLetterEventItemResult event = deadLetterEventRepository.findById(deadLetterEventId)
			.orElseThrow(AdminNotificationFailureNotFoundException::new);
		AdminNotificationReprocessRequestResult existing = reprocessRequestRepository
			.findByIdempotencyKey(normalizedKey, event.deadLetterEventId())
			.orElse(null);
		if (existing != null) {
			if (existing.conflict()) {
				throw new AdminNotificationReprocessConflictException();
			}
			AdminDeadLetterEventItemResult updated = deadLetterEventRepository.findById(event.deadLetterEventId())
				.orElseThrow(AdminNotificationFailureNotFoundException::new);
			return new AdminNotificationReprocessResult(
				updated.deadLetterEventId(), updated.status(), true, updated.reprocessedAt());
		}

		Instant requestedAt = clock.instant();
		AdminNotificationReprocessRequestResult saved = reprocessRequestRepository.save(
			new AdminNotificationReprocessRequestCommand(
				UUID.randomUUID(),
				normalizedKey,
				actor.id(),
				event.deadLetterEventId(),
				requestedAt));
		if (saved.conflict()) {
			throw new AdminNotificationReprocessConflictException();
		}
		if (!saved.duplicate()) {
			boolean reprocessStarted = deadLetterEventRepository
				.markReprocessed(event.deadLetterEventId(), actor.id(), requestedAt);
			if (!reprocessStarted) {
				throw new AdminNotificationReprocessConflictException();
			}
			auditLogRepository.save(new AdminAuditLogCommand(
				UUID.randomUUID(),
				actor.id(),
				actor.id(),
				"REPROCESS_NOTIFICATION_DLQ",
				"SUCCEEDED",
				"deadLetterEventId=%s duplicate=false".formatted(event.deadLetterEventId()),
				requestedAt));
			outboxEventRepository.save(OutboxEvent.pending(
				UUID.randomUUID(), event.eventType(), event.payload(), requestedAt));
		}
		AdminDeadLetterEventItemResult updated = deadLetterEventRepository.findById(event.deadLetterEventId())
			.orElseThrow(AdminNotificationFailureNotFoundException::new);
		return new AdminNotificationReprocessResult(
			updated.deadLetterEventId(), updated.status(), saved.duplicate(), updated.reprocessedAt());
	}

	private void validatePage(int page, int pageSize) {
		if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE
			|| (long) page > ((long) Integer.MAX_VALUE / pageSize) + 1) {
			throw new AdminInvalidNotificationFailureRequestException();
		}
	}

	private String normalizeIdempotencyKey(String value) {
		if (value == null) {
			throw new AdminInvalidNotificationFailureRequestException();
		}
		String normalized = value.trim();
		if (normalized.isEmpty() || normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new AdminInvalidNotificationFailureRequestException();
		}
		String upper = normalized.toUpperCase(Locale.ROOT);
		if (upper.contains(" ") || upper.contains("\t") || upper.contains("\n") || upper.contains("\r")) {
			throw new AdminInvalidNotificationFailureRequestException();
		}
		return normalized;
	}

	private void requireAdmin(User actor) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new AdminAuthorizationException();
		}
	}
}
