package com.allermeal.application.admin;

import java.util.List;

public record AdminUserAccessHistoryPageResult(
	List<AdminUserAccessHistoryItemResult> items,
	int page,
	int pageSize,
	long totalCount
) {
}
