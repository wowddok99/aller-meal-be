package com.allermeal.application.admin;

import java.time.Instant;

public record AdminDashboardSummaryResult(
	Instant generatedAt,
	AdminDashboardCollectionSummaryResult collection,
	AdminDashboardLabelingSummaryResult labeling,
	AdminDashboardOutboxSummaryResult outbox,
	AdminDashboardDeadLetterSummaryResult dlq,
	AdminDashboardNotificationSummaryResult notifications
) {
}
