package com.allermeal.application.admin;

import java.util.List;

public record AdminDeadLetterEventPageResult(
	List<AdminDeadLetterEventItemResult> items,
	int page,
	int pageSize,
	long totalCount
) {
}
