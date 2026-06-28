package com.allermeal.api.auth;

import com.allermeal.api.auth.request.SignupRequest;
import com.allermeal.application.auth.SignupCommand;
import com.allermeal.application.auth.SignupResult;
import com.allermeal.application.auth.SignupService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class TransactionalSignupHandler {

	private final SignupService signupService;

	TransactionalSignupHandler(SignupService signupService) {
		this.signupService = signupService;
	}

	@Transactional
	SignupResult signup(SignupRequest request) {
		return signupService.signup(new SignupCommand(request.email(), request.password()));
	}
}
