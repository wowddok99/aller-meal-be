package com.allermeal.application.admin;

import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.time.Instant;

public record AdminUserListItemResult(
	UserId userId,
	String email,
	UserRole role,
	UserStatus status,
	EmailVerificationStatus emailVerificationStatus,
	Instant createdAt
) {
}
