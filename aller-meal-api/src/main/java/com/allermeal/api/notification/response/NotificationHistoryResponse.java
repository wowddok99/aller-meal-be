package com.allermeal.api.notification.response;

import com.allermeal.application.notification.NotificationHistoryResult;
import java.util.List;

public record NotificationHistoryResponse(
	List<NotificationHistoryItemResponse> notifications,
	int page,
	int pageSize,
	int totalCount
) {

	public static NotificationHistoryResponse from(NotificationHistoryResult result) {
		return new NotificationHistoryResponse(
			result.notifications().stream().map(NotificationHistoryItemResponse::from).toList(),
			result.page(),
			result.pageSize(),
			result.totalCount());
	}
}
