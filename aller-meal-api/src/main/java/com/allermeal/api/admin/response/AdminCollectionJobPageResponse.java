package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminCollectionJobPageResult;
import java.util.List;

public record AdminCollectionJobPageResponse(List<AdminCollectionJobItemResponse> items, int page, int pageSize, long totalCount) {
	public static AdminCollectionJobPageResponse from(AdminCollectionJobPageResult result) {
		return new AdminCollectionJobPageResponse(result.items().stream().map(AdminCollectionJobItemResponse::from).toList(),
			result.page(), result.pageSize(), result.totalCount());
	}
}
