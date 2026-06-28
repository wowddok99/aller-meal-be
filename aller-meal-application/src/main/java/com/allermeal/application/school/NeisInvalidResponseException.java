package com.allermeal.application.school;

public final class NeisInvalidResponseException extends RuntimeException {

	public NeisInvalidResponseException(String message) {
		super(message);
	}

	public NeisInvalidResponseException(String message, Throwable cause) {
		super(message, cause);
	}
}
