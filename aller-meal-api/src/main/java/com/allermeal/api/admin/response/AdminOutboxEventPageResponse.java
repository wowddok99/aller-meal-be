package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminOutboxEventPageResult;
import java.util.List;

public record AdminOutboxEventPageResponse(List<AdminOutboxEventItemResponse> items, int page, int pageSize, long totalCount) {
	public static AdminOutboxEventPageResponse from(AdminOutboxEventPageResult result) {
		return new AdminOutboxEventPageResponse(result.items().stream().map(AdminOutboxEventItemResponse::from).toList(),
			result.page(), result.pageSize(), result.totalCount());
	}
}
