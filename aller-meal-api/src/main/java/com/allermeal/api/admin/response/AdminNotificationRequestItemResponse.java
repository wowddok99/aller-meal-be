package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminNotificationRequestItemResult;
import com.allermeal.domain.notification.NotificationChannel;
import com.allermeal.domain.notification.NotificationReason;
import com.allermeal.domain.notification.NotificationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminNotificationRequestItemResponse(
	UUID notificationId, UUID notificationTargetId, UUID childProfileId, UUID ownerId, LocalDate notificationDate,
	NotificationChannel channel, NotificationReason reason, NotificationStatus status, int attemptCount, int maxAttempts,
	Instant nextAttemptAt, Instant sentAt, String failureCode, String failureMessage, Instant createdAt, Instant updatedAt
) {
	public static AdminNotificationRequestItemResponse from(AdminNotificationRequestItemResult result) {
		return new AdminNotificationRequestItemResponse(result.notificationId().value(), result.notificationTargetId(),
			result.childProfileId().value(), result.ownerId().value(), result.notificationDate(), result.channel(), result.reason(),
			result.status(), result.attemptCount(), result.maxAttempts(), result.nextAttemptAt(), result.sentAt(),
			result.failureCode(), result.failureMessage(), result.createdAt(), result.updatedAt());
	}
}
