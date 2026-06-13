package com.allermeal.domain.meal;

import com.allermeal.domain.common.DomainIdentifier;
import java.util.Objects;
import java.util.UUID;

public record MealId(UUID value) implements DomainIdentifier {

	public MealId {
		Objects.requireNonNull(value, "급식 ID는 null일 수 없습니다.");
	}
}
