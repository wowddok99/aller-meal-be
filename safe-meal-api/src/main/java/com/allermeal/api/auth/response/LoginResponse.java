package com.allermeal.api.auth.response;

import com.allermeal.application.auth.AuthenticationResult;
import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
	UUID userId,
	String emailVerificationStatus,
	Instant accessTokenExpiresAt,
	Instant refreshTokenExpiresAt
) {

	public static LoginResponse from(AuthenticationResult result) {
		return new LoginResponse(
			result.userId().value(),
			result.emailVerificationStatus().name(),
			result.accessTokenExpiresAt(),
			result.refreshTokenExpiresAt());
	}
}
