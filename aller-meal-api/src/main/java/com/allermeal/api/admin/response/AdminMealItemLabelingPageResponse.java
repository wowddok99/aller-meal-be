package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminMealItemLabelingPageResult;
import java.util.List;

public record AdminMealItemLabelingPageResponse(List<AdminMealItemLabelingItemResponse> items, int page, int pageSize, long totalCount) {
	public static AdminMealItemLabelingPageResponse from(AdminMealItemLabelingPageResult result) {
		return new AdminMealItemLabelingPageResponse(result.items().stream().map(AdminMealItemLabelingItemResponse::from).toList(),
			result.page(), result.pageSize(), result.totalCount());
	}
}
