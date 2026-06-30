package com.allermeal.application.retention;

public record OperationLogRetentionDeletionResult(
	int adminAuditLogDeletedCount,
	int externalApiLogDeletedCount,
	int adminDashboardSummarySnapshotDeletedCount
) {

	public int totalDeletedCount() {
		return adminAuditLogDeletedCount + externalApiLogDeletedCount + adminDashboardSummarySnapshotDeletedCount;
	}
}
