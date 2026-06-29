package com.allermeal.application.port.out.command;

import com.allermeal.domain.user.UserId;
import java.time.Instant;
import java.util.UUID;

public record AdminNotificationReprocessRequestCommand(
	UUID reprocessRequestId,
	String idempotencyKey,
	UserId actorUserId,
	UUID deadLetterEventId,
	Instant createdAt
) {
}
