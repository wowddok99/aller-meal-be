package com.allermeal.api.account.response;

import com.allermeal.domain.user.UserStatus;
import com.allermeal.domain.user.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

public record AccountProfileResponse(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String displayName,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ACTIVE", "WITHDRAWAL_PENDING"}) UserStatus userStatus,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserRole role
) {
	@Override
	public String toString() { return "AccountProfileResponse[REDACTED]"; }
}
