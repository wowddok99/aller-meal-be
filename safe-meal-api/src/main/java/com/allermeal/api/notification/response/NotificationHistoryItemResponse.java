package com.allermeal.api.notification.response;

import com.allermeal.application.notification.NotificationHistoryItemResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NotificationHistoryItemResponse(
	UUID notificationId,
	LocalDate notificationDate,
	String channel,
	String reason,
	String status,
	int attemptCount,
	Instant sentAt,
	String failureCode,
	Instant createdAt,
	Instant updatedAt
) {

	public static NotificationHistoryItemResponse from(NotificationHistoryItemResult result) {
		return new NotificationHistoryItemResponse(
			result.notificationId().value(),
			result.notificationDate(),
			result.channel().name(),
			result.reason().name(),
			result.status().name(),
			result.attemptCount(),
			result.sentAt(),
			result.failureCode(),
			result.createdAt(),
			result.updatedAt());
	}
}
