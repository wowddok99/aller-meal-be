package com.allermeal.application.port.out.result;

import com.allermeal.application.admin.AdminCollectionRecoveryResult;
import java.time.Instant;

public record AdminCollectionRecoveryLookupResult(
	boolean unresolvedFailure, boolean hasActive, Instant latestExecutionAt, AdminCollectionRecoveryResult recovery
) {}
