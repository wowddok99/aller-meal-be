package com.allermeal.application.auth;

import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.UserId;
import java.time.Instant;

public record AuthenticationResult(
	UserId userId,
	EmailVerificationStatus emailVerificationStatus,
	String accessToken,
	Instant accessTokenExpiresAt,
	String refreshToken,
	Instant refreshTokenExpiresAt
) {
}
