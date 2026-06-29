package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminFailedCollectionJobItemResult;
import com.allermeal.domain.meal.MealType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminFailedCollectionJobItemResponse(
	UUID collectionJobId,
	UUID schoolId,
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

	public static AdminFailedCollectionJobItemResponse from(AdminFailedCollectionJobItemResult result) {
		return new AdminFailedCollectionJobItemResponse(
			result.collectionJobId().value(),
			result.schoolId().value(),
			result.mealDate(),
			result.mealType(),
			result.responseTimeMillis(),
			result.collectionDurationMillis(),
			result.rawObjectId(),
			result.failureCode(),
			result.failureMessage(),
			result.createdAt(),
			result.updatedAt());
	}
}
