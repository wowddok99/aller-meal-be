package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminDeadLetterEventPageResult;
import java.util.List;

public record AdminDeadLetterEventPageResponse(
	List<AdminDeadLetterEventItemResponse> items,
	int page,
	int pageSize,
	long totalCount
) {

	public static AdminDeadLetterEventPageResponse from(AdminDeadLetterEventPageResult result) {
		return new AdminDeadLetterEventPageResponse(
			result.items().stream().map(AdminDeadLetterEventItemResponse::from).toList(),
			result.page(),
			result.pageSize(),
			result.totalCount());
	}
}
