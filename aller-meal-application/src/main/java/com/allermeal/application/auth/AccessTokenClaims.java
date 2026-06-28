package com.allermeal.application.auth;

import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.time.Instant;

public record AccessTokenClaims(
	UserId userId,
	UserRole role,
	UserStatus status,
	EmailVerificationStatus emailVerificationStatus,
	Instant expiresAt
) {
}
