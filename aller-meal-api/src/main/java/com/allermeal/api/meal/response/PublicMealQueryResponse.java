package com.allermeal.api.meal.response;

import com.allermeal.application.meal.PublicMealQueryResult;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PublicMealQueryResponse(
	UUID schoolId,
	LocalDate rangeStart,
	LocalDate rangeEnd,
	String collectionStatus,
	Integer retryAfterSeconds,
	List<PublicMealResponse> meals,
	List<PublicMealTargetResponse> pendingTargets
) {

	public static PublicMealQueryResponse from(PublicMealQueryResult result) {
		return new PublicMealQueryResponse(
			result.schoolId().value(),
			result.rangeStart(),
			result.rangeEnd(),
			result.collectionStatus().name(),
			result.retryAfterSeconds(),
			result.meals().stream().map(PublicMealResponse::from).toList(),
			result.pendingTargets().stream().map(PublicMealTargetResponse::from).toList());
	}
}
