package com.allermeal.application.admin;

import com.allermeal.domain.collection.CollectionJobStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminCollectionRecoveryResult(
	AdminCollectionRecoveryStatus status, UUID latestCollectionJobId, CollectionJobStatus latestStatus,
	Instant requestedAt, Instant completedAt, UUID resolvedCollectionJobId, Instant resolvedAt
) {
	public static AdminCollectionRecoveryResult notRequested() {
		return new AdminCollectionRecoveryResult(AdminCollectionRecoveryStatus.NOT_REQUESTED, null, null, null, null, null, null);
	}
}
