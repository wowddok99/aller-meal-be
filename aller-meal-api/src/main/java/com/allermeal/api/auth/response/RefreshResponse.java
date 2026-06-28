package com.allermeal.api.auth.response;

import com.allermeal.application.auth.AuthenticationResult;
import java.time.Instant;
import java.util.UUID;

public record RefreshResponse(
	UUID userId,
	String emailVerificationStatus,
	Instant accessTokenExpiresAt,
	Instant refreshTokenExpiresAt
) {

	public static RefreshResponse from(AuthenticationResult result) {
		return new RefreshResponse(
			result.userId().value(),
			result.emailVerificationStatus().name(),
			result.accessTokenExpiresAt(),
			result.refreshTokenExpiresAt());
	}
}
