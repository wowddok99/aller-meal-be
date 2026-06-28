package com.allermeal.application.school;

public final class SchoolNotFoundException extends RuntimeException {

	public SchoolNotFoundException() {
		super("학교를 찾을 수 없습니다.");
	}
}
