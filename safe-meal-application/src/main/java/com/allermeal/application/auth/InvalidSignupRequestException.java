package com.allermeal.application.auth;

public final class InvalidSignupRequestException extends RuntimeException {

	public InvalidSignupRequestException(String message) {
		super(message);
	}
}
