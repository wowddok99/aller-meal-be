package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminUserListItemResult;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record AdminUserListItemResponse(
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
	Instant createdAt
) {

	public static AdminUserListItemResponse from(AdminUserListItemResult result) {
		return new AdminUserListItemResponse(
			result.userId().value(), result.email(), result.role(), AdminUserStatus.from(result.status()),
			result.emailVerificationStatus(), result.createdAt());
	}
}
