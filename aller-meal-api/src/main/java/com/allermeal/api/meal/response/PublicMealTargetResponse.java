package com.allermeal.api.meal.response;

import com.allermeal.application.meal.PublicMealTarget;
import java.time.LocalDate;

public record PublicMealTargetResponse(LocalDate mealDate, String mealType) {

	public static PublicMealTargetResponse from(PublicMealTarget target) {
		return new PublicMealTargetResponse(target.mealDate(), target.mealType().name());
	}
}
