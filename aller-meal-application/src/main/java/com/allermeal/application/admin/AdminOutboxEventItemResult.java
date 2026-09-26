package com.allermeal.application.admin;

import com.allermeal.domain.outbox.OutboxEventStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminOutboxEventItemResult(
	UUID eventId, String eventType, OutboxEventStatus status, Instant occurredAt, Instant publishedAt,
	Instant createdAt, Instant updatedAt
) {
}
