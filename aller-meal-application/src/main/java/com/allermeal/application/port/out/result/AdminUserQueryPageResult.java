package com.allermeal.application.port.out.result;

import java.util.List;

public record AdminUserQueryPageResult(
	List<AdminUserQueryResult> items,
	int page,
	int pageSize,
	long totalCount
) {
}
