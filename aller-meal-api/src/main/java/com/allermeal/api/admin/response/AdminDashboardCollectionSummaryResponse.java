package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminDashboardCollectionSummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminDashboardCollectionSummaryResponse(
	long pendingCount,
	long runningCount,
	long succeededCount,
	long failedCount,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", format = "int64") long unresolvedFailedCount
) {

	public static AdminDashboardCollectionSummaryResponse from(AdminDashboardCollectionSummaryResult result) {
		return new AdminDashboardCollectionSummaryResponse(
			result.pendingCount(),
			result.runningCount(),
			result.succeededCount(),
			result.failedCount(), result.unresolvedFailedCount());
	}
}
