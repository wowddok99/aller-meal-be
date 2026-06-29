package com.allermeal.application.port.out.command;

import java.time.Instant;
import java.util.UUID;

public record DeadLetterEventCommand(
	UUID deadLetterEventId,
	String messageId,
	String eventType,
	String payload,
	int retryCount,
	Instant createdAt
) {
}
