package com.allermeal.domain.user;

public record PasswordHash(String value) {

	public PasswordHash {
		value = UserValue.requireText(value, "비밀번호 해시");
	}

	@Override
	public String toString() {
		return "PasswordHash[REDACTED]";
	}
}
