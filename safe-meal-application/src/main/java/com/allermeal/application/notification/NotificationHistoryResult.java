package com.allermeal.application.notification;

import java.util.List;

public record NotificationHistoryResult(
	List<NotificationHistoryItemResult> notifications,
	int page,
	int pageSize,
	int totalCount
) {

	public NotificationHistoryResult {
		notifications = List.copyOf(notifications);
	}
}
