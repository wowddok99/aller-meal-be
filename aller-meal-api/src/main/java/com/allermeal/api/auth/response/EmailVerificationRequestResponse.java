package com.allermeal.api.auth.response;

import com.allermeal.application.auth.EmailVerificationRequestResult;

public record EmailVerificationRequestResponse(String emailVerificationStatus) {

	public static EmailVerificationRequestResponse from(EmailVerificationRequestResult result) {
		return new EmailVerificationRequestResponse(result.emailVerificationStatus().name());
	}
}
