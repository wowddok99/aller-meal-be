package com.allermeal.api.auth;

import com.allermeal.api.auth.request.EmailVerificationRequest;
import com.allermeal.api.auth.request.SignupRequest;
import com.allermeal.api.auth.response.EmailVerificationRequestResponse;
import com.allermeal.api.auth.response.SignupResponse;
import com.allermeal.application.auth.EmailVerificationRequestCommand;
import com.allermeal.application.auth.EmailVerificationRequester;
import com.allermeal.application.auth.InvalidSignupRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public final class AuthController {

	private final TransactionalSignupHandler signupHandler;
	private final EmailVerificationRequester verificationRequester;

	public AuthController(TransactionalSignupHandler signupHandler, EmailVerificationRequester verificationRequester) {
		this.signupHandler = signupHandler;
		this.verificationRequester = verificationRequester;
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public SignupResponse signup(@RequestBody SignupRequest request) {
		if (request == null) {
			throw new InvalidSignupRequestException("회원가입 요청 본문은 필수입니다.");
		}
		return SignupResponse.from(signupHandler.signup(request));
	}

	@PostMapping("/email-verifications")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public EmailVerificationRequestResponse requestEmailVerification(
		@RequestBody EmailVerificationRequest request
	) {
		if (request == null) {
			throw new InvalidSignupRequestException("이메일 인증 요청 본문은 필수입니다.");
		}
		return EmailVerificationRequestResponse.from(
			verificationRequester.request(new EmailVerificationRequestCommand(request.email())));
	}
}
