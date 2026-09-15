package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminUserAvailableActions;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminUserAvailableActionsResponse(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	boolean canPromoteToAdmin,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	boolean canSuspend,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	boolean canUnsuspend
) {

	public static AdminUserAvailableActionsResponse from(AdminUserAvailableActions actions) {
		return new AdminUserAvailableActionsResponse(
			actions.canPromoteToAdmin(), actions.canSuspend(), actions.canUnsuspend());
	}
}
