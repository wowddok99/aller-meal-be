package com.allermeal.api.auth;

import com.allermeal.api.auth.request.SignupRequest;
import tools.jackson.databind.JsonNode;
import com.allermeal.application.auth.InvalidSignupRequestException;
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
		return signupService.signup(new SignupCommand(request.email(), request.password(), text(request.displayName()), text(request.usageType())));
	}
	private String text(JsonNode value) {
		if (value == null || value.isNull()) return null;
		if (!value.isTextual()) throw new InvalidSignupRequestException("가입 정보 형식이 올바르지 않습니다.");
		return value.textValue();
	}
}
