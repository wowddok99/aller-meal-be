package com.allermeal.domain.allergen;

import java.util.Objects;

public record Allergen(int code, String name) {

	public Allergen {
		if (code <= 0) {
			throw new IllegalArgumentException("알레르기 코드는 양수여야 합니다.");
		}
		Objects.requireNonNull(name, "알레르기 이름은 null일 수 없습니다.");
		if (name.isBlank()) {
			throw new IllegalArgumentException("알레르기 이름은 비어 있을 수 없습니다.");
		}
	}
}
