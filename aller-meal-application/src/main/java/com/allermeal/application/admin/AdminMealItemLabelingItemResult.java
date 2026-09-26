package com.allermeal.application.admin;

import com.allermeal.domain.meal.MealId;
import com.allermeal.domain.meal.MealItemId;
import com.allermeal.domain.meal.MealItemLabelingStatus;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.time.Instant;
import java.time.LocalDate;

public record AdminMealItemLabelingItemResult(
	MealItemId mealItemId,
	MealId mealId,
	SchoolId schoolId,
	String schoolName,
	LocalDate mealDate,
	MealType mealType,
	String name,
	int displayOrder,
	MealItemLabelingStatus status,
	Instant createdAt,
	Instant updatedAt
) {
}
