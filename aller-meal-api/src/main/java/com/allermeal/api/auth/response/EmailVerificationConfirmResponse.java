package com.allermeal.api.auth.response;

import com.allermeal.application.auth.EmailVerificationConfirmResult;
import java.util.UUID;

public record EmailVerificationConfirmResponse(UUID userId, String emailVerificationStatus) {

	public static EmailVerificationConfirmResponse from(EmailVerificationConfirmResult result) {
		return new EmailVerificationConfirmResponse(
			result.userId().value(), result.emailVerificationStatus().name());
	}
}
