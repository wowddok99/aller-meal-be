package com.allermeal.application.port.out;

import com.allermeal.application.retention.OperationLogRetentionDeletionResult;
import java.time.Instant;

public interface OperationLogRetentionRepository {

	OperationLogRetentionDeletionResult deleteExpiredNonPersonalLogs(Instant createdBefore, int limitPerTable);
}
