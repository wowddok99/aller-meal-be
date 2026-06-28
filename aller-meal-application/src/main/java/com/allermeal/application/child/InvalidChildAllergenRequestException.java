package com.allermeal.application.child;

public final class InvalidChildAllergenRequestException extends RuntimeException {

	public InvalidChildAllergenRequestException() {
		super("자녀 알레르기 요청 값이 올바르지 않습니다.");
	}
}
