package com.allermeal.application.admin;

import java.util.List;

public record AdminMealItemLabelingPageResult(
	List<AdminMealItemLabelingItemResult> items,
	int page,
	int pageSize,
	long totalCount
) {
}
