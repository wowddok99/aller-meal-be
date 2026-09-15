package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminUserAccessHistoryItemResult;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record AdminUserAccessHistoryItemResponse(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	UUID eventId,
	@Schema(nullable = true)
	UUID actorUserId,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	String action,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
		description = "LEGACY_ADMIN_AUDIT 항목은 null이며 USER_ACCESS_AUDIT 항목에서는 필수입니다.")
	UserRole beforeRole,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
		description = "LEGACY_ADMIN_AUDIT 항목은 null이며 USER_ACCESS_AUDIT 항목에서는 필수입니다.")
	UserRole afterRole,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
		description = "LEGACY_ADMIN_AUDIT 항목은 null이며 USER_ACCESS_AUDIT 항목에서는 필수입니다.")
	UserStatus beforeStatus,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
		description = "LEGACY_ADMIN_AUDIT 항목은 null이며 USER_ACCESS_AUDIT 항목에서는 필수입니다.")
	UserStatus afterStatus,
	@Schema(nullable = true)
	String reason,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	Instant createdAt,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	String source
) {

	public static AdminUserAccessHistoryItemResponse from(AdminUserAccessHistoryItemResult result) {
		return new AdminUserAccessHistoryItemResponse(
			result.eventId(),
			result.actorUserId() == null ? null : result.actorUserId().value(),
			result.action(), result.beforeRole(), result.afterRole(), result.beforeStatus(), result.afterStatus(),
			result.reason(), result.createdAt(), result.source());
	}
}
