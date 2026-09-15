package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminUserPageResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdminUserPageResponse(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	List<AdminUserListItemResponse> items,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	int page,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	int pageSize,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	long totalCount
) {

	public static AdminUserPageResponse from(AdminUserPageResult result) {
		return new AdminUserPageResponse(
			result.items().stream().map(AdminUserListItemResponse::from).toList(),
			result.page(), result.pageSize(), result.totalCount());
	}
}
