package com.allermeal.application.admin;

import java.util.List;

public record AdminUserPageResult(
	List<AdminUserListItemResult> items,
	int page,
	int pageSize,
	long totalCount
) {
}
