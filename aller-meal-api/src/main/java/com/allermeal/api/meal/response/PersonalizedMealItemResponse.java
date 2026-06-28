package com.allermeal.api.meal.response;

import com.allermeal.domain.meal.MealItem;
import com.allermeal.domain.risk.MealItemRisk;
import java.util.List;

public record PersonalizedMealItemResponse(
	String name,
	String rawText,
	int displayOrder,
	String labelingStatus,
	String riskLevel,
	List<Integer> matchedAllergenCodes
) {

	public static PersonalizedMealItemResponse from(MealItem item, MealItemRisk risk) {
		return new PersonalizedMealItemResponse(
			item.name(),
			item.rawText(),
			item.displayOrder(),
			item.labelingStatus().name(),
			risk.riskLevel().name(),
			risk.matchedAllergenCodes());
	}
}
