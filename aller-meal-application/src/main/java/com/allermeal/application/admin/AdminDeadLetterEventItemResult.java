package com.allermeal.application.admin;

import com.allermeal.domain.user.UserId;
import java.time.Instant;
import java.util.UUID;

public record AdminDeadLetterEventItemResult(
	UUID deadLetterEventId,
	String messageId,
	String eventType,
	String payload,
	int retryCount,
	AdminDeadLetterEventStatus status,
	UserId reprocessedBy,
	Instant reprocessedAt,
	Instant createdAt,
	Instant updatedAt
) {
}
