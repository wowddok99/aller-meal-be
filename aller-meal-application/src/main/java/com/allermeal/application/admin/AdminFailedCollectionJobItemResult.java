package com.allermeal.application.admin;

import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminFailedCollectionJobItemResult(
	CollectionJobId collectionJobId,
	SchoolId schoolId,
	LocalDate mealDate,
	MealType mealType,
	Long responseTimeMillis,
	Long collectionDurationMillis,
	UUID rawObjectId,
	String failureCode,
	String failureMessage,
	Instant createdAt,
	Instant updatedAt
) {
}
