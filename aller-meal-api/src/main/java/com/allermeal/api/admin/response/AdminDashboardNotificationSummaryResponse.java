package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminDashboardNotificationSummaryResult;

public record AdminDashboardNotificationSummaryResponse(
	long pendingCount,
	long sendingCount,
	long retryPendingCount,
	long sentCount,
	long failedCount,
	long canceledCount
) {

	public static AdminDashboardNotificationSummaryResponse from(AdminDashboardNotificationSummaryResult result) {
		return new AdminDashboardNotificationSummaryResponse(
			result.pendingCount(),
			result.sendingCount(),
			result.retryPendingCount(),
			result.sentCount(),
			result.failedCount(),
			result.canceledCount());
	}
}
