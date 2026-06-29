package com.allermeal.application.admin;

import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.notification.NotificationChannel;
import com.allermeal.domain.notification.NotificationId;
import com.allermeal.domain.notification.NotificationReason;
import com.allermeal.domain.notification.NotificationStatus;
import com.allermeal.domain.user.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminFailedNotificationItemResult(
	NotificationId notificationId,
	UUID notificationTargetId,
	ChildProfileId childProfileId,
	UserId ownerId,
	LocalDate notificationDate,
	NotificationChannel channel,
	NotificationReason reason,
	NotificationStatus status,
	int attemptCount,
	int maxAttempts,
	String failureCode,
	String failureMessage,
	Instant createdAt,
	Instant updatedAt
) {
}
