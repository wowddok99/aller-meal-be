package com.allermeal.application.admin;

import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminCollectionJobItemResult(
	CollectionJobId collectionJobId,
	SchoolId schoolId,
	String schoolName,
	LocalDate mealDate,
	MealType mealType,
	CollectionJobStatus status,
	Long responseTimeMillis,
	Long collectionDurationMillis,
	Instant leaseUntil,
	UUID rawObjectId,
	String failureCode,
	String failureMessage,
	Instant createdAt,
	Instant updatedAt
) {
}
