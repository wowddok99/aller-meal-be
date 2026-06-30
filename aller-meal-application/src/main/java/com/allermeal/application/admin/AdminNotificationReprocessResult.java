package com.allermeal.application.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminNotificationReprocessResult(
	UUID deadLetterEventId,
	AdminDeadLetterEventStatus status,
	boolean duplicate,
	Instant reprocessedAt
) {
}
