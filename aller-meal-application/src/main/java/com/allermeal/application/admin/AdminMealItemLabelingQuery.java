package com.allermeal.application.admin;

import com.allermeal.domain.meal.MealItemLabelingStatus;
import com.allermeal.domain.meal.MealType;
import java.time.LocalDate;
import java.util.UUID;

public record AdminMealItemLabelingQuery(
	int page,
	int pageSize,
	MealItemLabelingStatus status,
	UUID schoolId,
	LocalDate mealDate,
	MealType mealType,
	String query
) {
}
