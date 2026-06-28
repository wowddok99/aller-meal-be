package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminUserRoleResult;
import com.allermeal.domain.user.UserRole;
import java.util.UUID;

public record AdminUserRoleResponse(
	UUID userId,
	UserRole role
) {

	public static AdminUserRoleResponse from(AdminUserRoleResult result) {
		return new AdminUserRoleResponse(result.userId().value(), result.role());
	}
}
