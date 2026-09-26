package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminNotificationRequestPageResult;
import java.util.List;

public record AdminNotificationRequestPageResponse(List<AdminNotificationRequestItemResponse> items, int page, int pageSize, long totalCount) {
	public static AdminNotificationRequestPageResponse from(AdminNotificationRequestPageResult result) {
		return new AdminNotificationRequestPageResponse(result.items().stream().map(AdminNotificationRequestItemResponse::from).toList(),
			result.page(), result.pageSize(), result.totalCount());
	}
}
