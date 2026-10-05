package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminCollectionAvailableActionsResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AdminCollectionAvailableActionsResponse(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean canRecollect,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean canExecute,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant executeAvailableAt
) {
	public static AdminCollectionAvailableActionsResponse from(AdminCollectionAvailableActionsResult r) {
		return new AdminCollectionAvailableActionsResponse(r.canRecollect(), r.canExecute(), r.executeAvailableAt());
	}
}
