package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminFailedNotificationItemResult;
import com.allermeal.domain.notification.NotificationChannel;
import com.allermeal.domain.notification.NotificationReason;
import com.allermeal.domain.notification.NotificationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminFailedNotificationItemResponse(
	UUID notificationId,
	UUID notificationTargetId,
	UUID childId,
	UUID userId,
	LocalDate notificationDate,
	NotificationChannel channel,
	NotificationReason reason,
	NotificationStatus status,
	int attemptCount,
	int maxAttempts,
	String failureCode,
	Instant createdAt,
	Instant updatedAt
) {

	public static AdminFailedNotificationItemResponse from(AdminFailedNotificationItemResult result) {
		return new AdminFailedNotificationItemResponse(
			result.notificationId().value(),
			result.notificationTargetId(),
			result.childProfileId().value(),
			result.ownerId().value(),
			result.notificationDate(),
			result.channel(),
			result.reason(),
			result.status(),
			result.attemptCount(),
			result.maxAttempts(),
			result.failureCode(),
			result.createdAt(),
			result.updatedAt());
	}
}
