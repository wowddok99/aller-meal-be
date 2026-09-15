package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminUserDetailResult;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record AdminUserDetailResponse(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	UUID userId,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	String email,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	UserRole role,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	AdminUserStatus status,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	EmailVerificationStatus emailVerificationStatus,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	Instant createdAt,
	@Schema(nullable = true)
	Instant withdrawalDueAt,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	long version,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	AdminUserAvailableActionsResponse availableActions
) {

	public static AdminUserDetailResponse from(AdminUserDetailResult result) {
		return new AdminUserDetailResponse(
			result.userId().value(), result.email(), result.role(), AdminUserStatus.from(result.status()),
			result.emailVerificationStatus(), result.createdAt(), result.withdrawalDueAt(), result.version(),
			AdminUserAvailableActionsResponse.from(result.availableActions()));
	}
}
