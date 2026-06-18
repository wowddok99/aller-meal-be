package com.allermeal.api.auth;

import com.allermeal.api.auth.request.EmailVerificationRequest;
import com.allermeal.api.auth.request.LoginRequest;
import com.allermeal.api.auth.request.SignupRequest;
import com.allermeal.api.auth.response.EmailVerificationConfirmResponse;
import com.allermeal.api.auth.response.EmailVerificationRequestResponse;
import com.allermeal.api.auth.response.LoginResponse;
import com.allermeal.api.auth.response.SignupResponse;
import com.allermeal.application.auth.AuthenticationResult;
import com.allermeal.application.auth.EmailVerificationConfirmer;
import com.allermeal.application.auth.EmailVerificationRequestCommand;
import com.allermeal.application.auth.EmailVerificationRequester;
import com.allermeal.application.auth.InvalidSignupRequestException;
import com.allermeal.application.auth.LoginCommand;
import com.allermeal.application.auth.LoginService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public final class AuthController {

	private final TransactionalSignupHandler signupHandler;
	private final EmailVerificationRequester verificationRequester;
	private final EmailVerificationConfirmer verificationConfirmer;
	private final LoginService loginService;
	private final AuthCookieWriter cookieWriter;

	public AuthController(
		TransactionalSignupHandler signupHandler,
		EmailVerificationRequester verificationRequester,
		EmailVerificationConfirmer verificationConfirmer,
		LoginService loginService,
		AuthCookieWriter cookieWriter
	) {
		this.signupHandler = signupHandler;
		this.verificationRequester = verificationRequester;
		this.verificationConfirmer = verificationConfirmer;
		this.loginService = loginService;
		this.cookieWriter = cookieWriter;
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

	@GetMapping("/email-verifications/confirm")
	public EmailVerificationConfirmResponse confirmEmailVerification(@RequestParam String token) {
		return EmailVerificationConfirmResponse.from(verificationConfirmer.confirm(token));
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
		if (request == null) {
			throw new InvalidSignupRequestException("로그인 요청 본문은 필수입니다.");
		}
		AuthenticationResult result = loginService.login(new LoginCommand(request.email(), request.password()));
		cookieWriter.writeAuthenticationCookies(result, response);
		return ResponseEntity.ok(LoginResponse.from(result));
	}
}
