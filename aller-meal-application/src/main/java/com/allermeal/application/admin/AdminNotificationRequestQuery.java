package com.allermeal.application.admin;

import com.allermeal.domain.notification.NotificationChannel;
import com.allermeal.domain.notification.NotificationReason;
import com.allermeal.domain.notification.NotificationStatus;

public record AdminNotificationRequestQuery(
	int page, int pageSize, NotificationStatus status, NotificationChannel channel, NotificationReason reason, String query
) {
}
