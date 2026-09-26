package com.allermeal.application.admin;

import java.util.List;

public record AdminNotificationRequestPageResult(
	List<AdminNotificationRequestItemResult> items, int page, int pageSize, long totalCount
) {
}
