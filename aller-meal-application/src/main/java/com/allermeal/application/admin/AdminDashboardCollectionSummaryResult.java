package com.allermeal.application.admin;

public record AdminDashboardCollectionSummaryResult(
	long pendingCount,
	long runningCount,
	long succeededCount,
	long failedCount
) {
}
