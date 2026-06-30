package com.allermeal.application.admin;

public record AdminDashboardNotificationSummaryResult(
	long pendingCount,
	long sendingCount,
	long retryPendingCount,
	long sentCount,
	long failedCount,
	long canceledCount
) {
}
