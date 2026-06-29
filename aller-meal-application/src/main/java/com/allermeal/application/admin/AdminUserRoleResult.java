package com.allermeal.application.admin;

import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;

public record AdminUserRoleResult(
	UserId userId,
	UserRole role
) {
}
