package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminCollectionRecoveryResult;
import com.allermeal.application.admin.AdminCollectionRecoveryStatus;
import com.allermeal.domain.collection.CollectionJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record AdminCollectionRecoveryResponse(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdminCollectionRecoveryStatus status,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID latestCollectionJobId,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) CollectionJobStatus latestStatus,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant requestedAt,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant completedAt,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID resolvedCollectionJobId,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant resolvedAt
) {
	public static AdminCollectionRecoveryResponse from(AdminCollectionRecoveryResult r) {
		return new AdminCollectionRecoveryResponse(r.status(), r.latestCollectionJobId(), r.latestStatus(),
			r.requestedAt(), r.completedAt(), r.resolvedCollectionJobId(), r.resolvedAt());
	}
}
