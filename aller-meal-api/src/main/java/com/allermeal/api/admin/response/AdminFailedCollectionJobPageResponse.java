package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminFailedCollectionJobPageResult;
import java.util.List;

public record AdminFailedCollectionJobPageResponse(
	List<AdminFailedCollectionJobItemResponse> items,
	int page,
	int pageSize,
	long totalCount
) {

	public static AdminFailedCollectionJobPageResponse from(AdminFailedCollectionJobPageResult result) {
		return new AdminFailedCollectionJobPageResponse(
			result.items().stream().map(AdminFailedCollectionJobItemResponse::from).toList(),
			result.page(),
			result.pageSize(),
			result.totalCount());
	}
}
