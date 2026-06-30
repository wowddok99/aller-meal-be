package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminDashboardCollectionSummaryResult;

public record AdminDashboardCollectionSummaryResponse(
	long pendingCount,
	long runningCount,
	long succeededCount,
	long failedCount
) {

	public static AdminDashboardCollectionSummaryResponse from(AdminDashboardCollectionSummaryResult result) {
		return new AdminDashboardCollectionSummaryResponse(
			result.pendingCount(),
			result.runningCount(),
			result.succeededCount(),
			result.failedCount());
	}
}
