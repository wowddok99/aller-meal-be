package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminDashboardSummaryResult;
import java.time.Instant;

public record AdminDashboardSummaryResponse(
	Instant generatedAt,
	AdminDashboardCollectionSummaryResponse collection,
	AdminDashboardLabelingSummaryResponse labeling,
	AdminDashboardOutboxSummaryResponse outbox,
	AdminDashboardDeadLetterSummaryResponse dlq,
	AdminDashboardNotificationSummaryResponse notifications
) {

	public static AdminDashboardSummaryResponse from(AdminDashboardSummaryResult result) {
		return new AdminDashboardSummaryResponse(
			result.generatedAt(),
			AdminDashboardCollectionSummaryResponse.from(result.collection()),
			AdminDashboardLabelingSummaryResponse.from(result.labeling()),
			AdminDashboardOutboxSummaryResponse.from(result.outbox()),
			AdminDashboardDeadLetterSummaryResponse.from(result.dlq()),
			AdminDashboardNotificationSummaryResponse.from(result.notifications()));
	}
}
