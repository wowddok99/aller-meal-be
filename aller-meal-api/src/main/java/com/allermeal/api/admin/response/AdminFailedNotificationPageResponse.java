package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminFailedNotificationPageResult;
import java.util.List;

public record AdminFailedNotificationPageResponse(
	List<AdminFailedNotificationItemResponse> items,
	int page,
	int pageSize,
	long totalCount
) {

	public static AdminFailedNotificationPageResponse from(AdminFailedNotificationPageResult result) {
		return new AdminFailedNotificationPageResponse(
			result.items().stream().map(AdminFailedNotificationItemResponse::from).toList(),
			result.page(),
			result.pageSize(),
			result.totalCount());
	}
}
