package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminExternalApiLogPageResult;
import java.util.List;

public record AdminExternalApiLogPageResponse(
	List<AdminExternalApiLogItemResponse> items,
	int page,
	int pageSize,
	int totalCount
) {

	public static AdminExternalApiLogPageResponse from(AdminExternalApiLogPageResult result) {
		return new AdminExternalApiLogPageResponse(
			result.items().stream().map(AdminExternalApiLogItemResponse::from).toList(),
			result.page(),
			result.pageSize(),
			result.totalCount());
	}
}
