package com.allermeal.application.admin;

public record AdminDashboardOutboxSummaryResult(
	long pendingCount,
	long publishedCount
) {
}
