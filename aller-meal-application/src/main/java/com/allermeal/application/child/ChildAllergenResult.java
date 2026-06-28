package com.allermeal.application.child;

import com.allermeal.domain.child.ChildProfileId;
import java.util.List;
import java.util.Objects;

public record ChildAllergenResult(ChildProfileId childProfileId, List<Integer> allergenCodes) {

	public ChildAllergenResult {
		Objects.requireNonNull(childProfileId, "자녀 ID는 null일 수 없습니다.");
		allergenCodes = List.copyOf(allergenCodes);
	}
}
