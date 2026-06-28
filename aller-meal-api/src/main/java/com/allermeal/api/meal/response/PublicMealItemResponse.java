package com.allermeal.api.meal.response;

import com.allermeal.domain.meal.MealItem;

public record PublicMealItemResponse(
	String name,
	String rawText,
	int displayOrder,
	String labelingStatus
) {

	public static PublicMealItemResponse from(MealItem item) {
		return new PublicMealItemResponse(
			item.name(), item.rawText(), item.displayOrder(), item.labelingStatus().name());
	}
}
