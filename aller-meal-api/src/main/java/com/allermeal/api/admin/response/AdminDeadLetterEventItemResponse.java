package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminDeadLetterEventItemResult;
import com.allermeal.application.admin.AdminDeadLetterEventStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminDeadLetterEventItemResponse(
	UUID deadLetterEventId,
	String messageId,
	String eventType,
	int retryCount,
	AdminDeadLetterEventStatus status,
	UUID reprocessedByUserId,
	Instant reprocessedAt,
	Instant createdAt,
	Instant updatedAt
) {

	public static AdminDeadLetterEventItemResponse from(AdminDeadLetterEventItemResult result) {
		return new AdminDeadLetterEventItemResponse(
			result.deadLetterEventId(),
			result.messageId(),
			result.eventType(),
			result.retryCount(),
			result.status(),
			result.reprocessedBy() == null ? null : result.reprocessedBy().value(),
			result.reprocessedAt(),
			result.createdAt(),
			result.updatedAt());
	}
}
