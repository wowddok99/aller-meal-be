package com.allermeal.application.notification;

import com.allermeal.domain.notification.NotificationChannel;
import com.allermeal.domain.notification.NotificationId;
import com.allermeal.domain.notification.NotificationReason;
import com.allermeal.domain.notification.NotificationStatus;
import java.time.Instant;
import java.time.LocalDate;

public record NotificationHistoryItemResult(
	NotificationId notificationId,
	LocalDate notificationDate,
	NotificationChannel channel,
	NotificationReason reason,
	NotificationStatus status,
	int attemptCount,
	Instant sentAt,
	String failureCode,
	Instant createdAt,
	Instant updatedAt
) {
}
