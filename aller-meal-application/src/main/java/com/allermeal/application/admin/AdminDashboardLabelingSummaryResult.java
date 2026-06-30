package com.allermeal.application.admin;

public record AdminDashboardLabelingSummaryResult(
	long pendingCount,
	long labeledCount,
	long unknownCount,
	long labelingFailedCount
) {
}
