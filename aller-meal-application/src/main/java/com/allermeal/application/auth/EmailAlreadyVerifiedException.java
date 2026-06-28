package com.allermeal.application.auth;

public final class EmailAlreadyVerifiedException extends RuntimeException {

	public EmailAlreadyVerifiedException() {
		super("이미 인증된 이메일입니다.");
	}
}
