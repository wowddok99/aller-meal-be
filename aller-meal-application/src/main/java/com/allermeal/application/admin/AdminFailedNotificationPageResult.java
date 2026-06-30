package com.allermeal.application.admin;

import java.util.List;

public record AdminFailedNotificationPageResult(
	List<AdminFailedNotificationItemResult> items,
	int page,
	int pageSize,
	long totalCount
) {
}
