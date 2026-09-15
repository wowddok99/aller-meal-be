package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminUserAccessHistoryPageResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdminUserAccessHistoryPageResponse(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	List<AdminUserAccessHistoryItemResponse> items,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	int page,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	int pageSize,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	long totalCount
) {

	public static AdminUserAccessHistoryPageResponse from(AdminUserAccessHistoryPageResult result) {
		return new AdminUserAccessHistoryPageResponse(
			result.items().stream().map(AdminUserAccessHistoryItemResponse::from).toList(),
			result.page(), result.pageSize(), result.totalCount());
	}
}
