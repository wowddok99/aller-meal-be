package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminDashboardOutboxSummaryResult;

public record AdminDashboardOutboxSummaryResponse(
	long pendingCount,
	long publishedCount
) {

	public static AdminDashboardOutboxSummaryResponse from(AdminDashboardOutboxSummaryResult result) {
		return new AdminDashboardOutboxSummaryResponse(result.pendingCount(), result.publishedCount());
	}
}
