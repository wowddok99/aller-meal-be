package com.allermeal.application.retention;

import java.time.Instant;

public record DataRetentionCleanupResult(
	Instant rawPayloadExpiresBeforeInclusive,
	Instant operationLogCreatedBefore,
	int rawPayloadScannedCount,
	int rawPayloadDeletedCount,
	int rawPayloadFailedCount,
	OperationLogRetentionDeletionResult operationLogDeletion
) {
}
