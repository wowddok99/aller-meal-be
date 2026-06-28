package com.allermeal.domain.user;

import java.util.Objects;

final class UserValue {

	private UserValue() {
	}

	static String requireText(String value, String name) {
		Objects.requireNonNull(value, name + "은 null일 수 없습니다.");
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + "은 비어 있을 수 없습니다.");
		}
		return value;
	}
}
