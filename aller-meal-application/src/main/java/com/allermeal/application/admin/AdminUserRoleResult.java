package com.allermeal.application.admin;

import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.time.Instant;

public record AdminUserRoleResult(
	UserId userId,
	UserRole role,
	UserStatus status,
	long version,
	String action,
	Instant changedAt
) {
}
