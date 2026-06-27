package com.allermeal.application.meal;

import com.allermeal.domain.meal.Meal;

public final class MealLabelingEvents {

	public static final String MEAL_COLLECTED = "MealCollected";
	public static final String MEAL_LABELED = "MealLabeled";

	private MealLabelingEvents() {
	}

	public static String mealCollectedPayload(Meal meal) {
		return mealPayload(meal);
	}

	public static String mealLabeledPayload(Meal meal) {
		return mealPayload(meal);
	}

	private static String mealPayload(Meal meal) {
		return """
			{"mealId":"%s","schoolId":"%s","mealDate":"%s","mealType":"%s","labelingStatus":"%s","sourceVersion":"%s"}
			""".formatted(
			meal.id().value(), meal.schoolId().value(), meal.mealDate(), meal.mealType(), meal.labelingStatus(),
			escapeJson(meal.sourceVersion())).trim();
	}

	private static String escapeJson(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
