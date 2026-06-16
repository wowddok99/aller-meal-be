package com.allermeal.api.auth.response;

import com.allermeal.application.auth.SignupResult;
import java.util.UUID;

public record SignupResponse(UUID userId, String emailVerificationStatus) {

	public static SignupResponse from(SignupResult result) {
		return new SignupResponse(result.userId().value(), result.emailVerificationStatus().name());
	}
}
