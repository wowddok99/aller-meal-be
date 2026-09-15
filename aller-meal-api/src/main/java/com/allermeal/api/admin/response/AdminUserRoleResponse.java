package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminUserRoleResult;
import com.allermeal.domain.user.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record AdminUserRoleResponse(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	UUID userId,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	UserRole role,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	AdminUserStatus status,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	long version,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	String action,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	Instant changedAt
) {

	public static AdminUserRoleResponse from(AdminUserRoleResult result) {
		return new AdminUserRoleResponse(
			result.userId().value(), result.role(), AdminUserStatus.from(result.status()), result.version(), result.action(), result.changedAt());
	}
}
