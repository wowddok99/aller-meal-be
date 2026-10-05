package com.allermeal.application.admin;

import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.meal.MealType;
import java.time.LocalDate;
import java.util.UUID;

public record AdminCollectionJobQuery(
	int page,
	int pageSize,
	CollectionJobStatus status,
	UUID schoolId,
	LocalDate mealDate,
	MealType mealType,
	String query,
	Boolean unresolvedFailure
) {
	public AdminCollectionJobQuery(int page, int pageSize, CollectionJobStatus status, UUID schoolId,
		LocalDate mealDate, MealType mealType, String query) {
		this(page, pageSize, status, schoolId, mealDate, mealType, query, null);
	}
}
