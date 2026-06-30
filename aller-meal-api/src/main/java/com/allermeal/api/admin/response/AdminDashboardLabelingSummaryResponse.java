package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminDashboardLabelingSummaryResult;

public record AdminDashboardLabelingSummaryResponse(
	long pendingCount,
	long labeledCount,
	long unknownCount,
	long labelingFailedCount
) {

	public static AdminDashboardLabelingSummaryResponse from(AdminDashboardLabelingSummaryResult result) {
		return new AdminDashboardLabelingSummaryResponse(
			result.pendingCount(),
			result.labeledCount(),
			result.unknownCount(),
			result.labelingFailedCount());
	}
}
