package com.allermeal.api.auth.response;

import com.allermeal.application.auth.AuthenticationResult;
import com.allermeal.domain.user.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
	UUID userId,
	String emailVerificationStatus,
	Instant accessTokenExpiresAt,
	Instant refreshTokenExpiresAt,
	String sessionId,
	UserStatus userStatus
) {

	public static LoginResponse from(AuthenticationResult result) {
		return new LoginResponse(
			result.userId().value(),
			result.emailVerificationStatus().name(),
			result.accessTokenExpiresAt(),
			result.refreshTokenExpiresAt(),
			result.sessionId(),
			result.userStatus());
	}
}
