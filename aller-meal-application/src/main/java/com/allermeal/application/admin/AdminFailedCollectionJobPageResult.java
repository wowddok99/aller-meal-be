package com.allermeal.application.admin;

import java.util.List;

public record AdminFailedCollectionJobPageResult(
	List<AdminFailedCollectionJobItemResult> items,
	int page,
	int pageSize,
	long totalCount
) {
}
