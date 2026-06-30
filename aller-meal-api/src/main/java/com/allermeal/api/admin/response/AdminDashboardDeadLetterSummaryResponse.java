package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminDashboardDeadLetterSummaryResult;

public record AdminDashboardDeadLetterSummaryResponse(
	long pendingCount,
	long reprocessedCount
) {

	public static AdminDashboardDeadLetterSummaryResponse from(AdminDashboardDeadLetterSummaryResult result) {
		return new AdminDashboardDeadLetterSummaryResponse(result.pendingCount(), result.reprocessedCount());
	}
}
