package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminOutboxEventItemResult;
import com.allermeal.domain.outbox.OutboxEventStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminOutboxEventItemResponse(UUID eventId, String eventType, OutboxEventStatus status,
	Instant occurredAt, Instant publishedAt, Instant createdAt, Instant updatedAt) {
	public static AdminOutboxEventItemResponse from(AdminOutboxEventItemResult result) {
		return new AdminOutboxEventItemResponse(result.eventId(), result.eventType(), result.status(), result.occurredAt(),
			result.publishedAt(), result.createdAt(), result.updatedAt());
	}
}
