package com.allermeal.api.meal.response;

import com.allermeal.domain.meal.Meal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PublicMealResponse(
	UUID mealId,
	LocalDate mealDate,
	String mealType,
	Instant sourceReceivedAt,
	String labelingStatus,
	String nutritionInfo,
	String originInfo,
	List<PublicMealItemResponse> items
) {

	public static PublicMealResponse from(Meal meal) {
		return new PublicMealResponse(
			meal.id().value(),
			meal.mealDate(),
			meal.mealType().name(),
			meal.sourceReceivedAt(),
			meal.labelingStatus().name(),
			meal.nutritionInfo(),
			meal.originInfo(),
			meal.items().stream().map(PublicMealItemResponse::from).toList());
	}
}
