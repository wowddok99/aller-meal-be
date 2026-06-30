package com.allermeal.application.admin;

public record AdminDashboardDeadLetterSummaryResult(
	long pendingCount,
	long reprocessedCount
) {
}
