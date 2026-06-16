package com.allermeal.application.auth;

public final class UserEmailNotFoundException extends RuntimeException {

	public UserEmailNotFoundException() {
		super("요청한 이메일 계정을 찾을 수 없습니다.");
	}
}
