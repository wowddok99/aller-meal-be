package com.allermeal.domain.risk;

import com.allermeal.domain.meal.MealItemId;
import com.allermeal.domain.meal.MealItemLabelingStatus;
import java.util.List;
import java.util.Objects;

public record MealItemRisk(
	MealItemId mealItemId,
	MealItemLabelingStatus labelingStatus,
	MealRiskLevel riskLevel,
	List<Integer> matchedAllergenCodes
) {

	public MealItemRisk {
		Objects.requireNonNull(mealItemId, "메뉴 ID는 null일 수 없습니다.");
		Objects.requireNonNull(labelingStatus, "메뉴 라벨링 상태는 null일 수 없습니다.");
		Objects.requireNonNull(riskLevel, "메뉴 위험도는 null일 수 없습니다.");
		matchedAllergenCodes = List.copyOf(
			Objects.requireNonNull(matchedAllergenCodes, "매칭 알레르기 코드는 null일 수 없습니다."));
	}
}
