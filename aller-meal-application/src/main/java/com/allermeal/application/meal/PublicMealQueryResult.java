package com.allermeal.application.meal;

import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.school.SchoolId;
import java.time.LocalDate;
import java.util.List;

public record PublicMealQueryResult(
	SchoolId schoolId,
	LocalDate rangeStart,
	LocalDate rangeEnd,
	PublicMealCollectionStatus collectionStatus,
	Integer retryAfterSeconds,
	List<Meal> meals,
	List<PublicMealTarget> pendingTargets
) {

	public PublicMealQueryResult {
		meals = List.copyOf(meals);
		pendingTargets = List.copyOf(pendingTargets);
	}
}
