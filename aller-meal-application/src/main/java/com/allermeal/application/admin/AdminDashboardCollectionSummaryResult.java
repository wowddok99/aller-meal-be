package com.allermeal.application.admin;

public record AdminDashboardCollectionSummaryResult(
	long pendingCount,
	long runningCount,
	long succeededCount,
	long failedCount,
	long unresolvedFailedCount
) {
	public AdminDashboardCollectionSummaryResult(long pending, long running, long succeeded, long failed) {
		this(pending, running, succeeded, failed, failed);
	}
}
