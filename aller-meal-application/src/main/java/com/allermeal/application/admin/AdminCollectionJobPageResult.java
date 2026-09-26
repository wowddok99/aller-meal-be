package com.allermeal.application.admin;

import java.util.List;

public record AdminCollectionJobPageResult(
	List<AdminCollectionJobItemResult> items,
	int page,
	int pageSize,
	long totalCount
) {
}
