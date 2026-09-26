package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminMealItemLabelingItemResult;
import com.allermeal.domain.meal.MealItemLabelingStatus;
import com.allermeal.domain.meal.MealType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminMealItemLabelingItemResponse(
	UUID mealItemId, UUID mealId, UUID schoolId, String schoolName, LocalDate mealDate, MealType mealType,
	String name, int displayOrder, MealItemLabelingStatus status, Instant createdAt, Instant updatedAt
) {
	public static AdminMealItemLabelingItemResponse from(AdminMealItemLabelingItemResult result) {
		return new AdminMealItemLabelingItemResponse(result.mealItemId().value(), result.mealId().value(), result.schoolId().value(),
			result.schoolName(), result.mealDate(), result.mealType(), result.name(), result.displayOrder(), result.status(),
			result.createdAt(), result.updatedAt());
	}
}
