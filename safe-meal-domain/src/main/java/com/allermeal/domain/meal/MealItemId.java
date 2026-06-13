package com.allermeal.domain.meal;

import com.allermeal.domain.common.DomainIdentifier;
import java.util.Objects;
import java.util.UUID;

public record MealItemId(UUID value) implements DomainIdentifier {

	public MealItemId {
		Objects.requireNonNull(value, "메뉴 ID는 null일 수 없습니다.");
	}
}
