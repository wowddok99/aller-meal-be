package com.allermeal.application.admin;

import java.util.List;

public record AdminExternalApiLogPageResult(
	List<AdminExternalApiLogItemResult> items,
	int page,
	int pageSize,
	long totalCount
) {
}
