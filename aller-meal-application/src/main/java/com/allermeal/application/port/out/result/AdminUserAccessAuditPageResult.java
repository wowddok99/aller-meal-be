package com.allermeal.application.port.out.result;

import java.util.List;

public record AdminUserAccessAuditPageResult(
	List<AdminUserAccessAuditResult> items,
	int page,
	int pageSize,
	long totalCount
) {
}
