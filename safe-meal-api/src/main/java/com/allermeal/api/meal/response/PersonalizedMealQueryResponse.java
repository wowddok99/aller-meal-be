package com.allermeal.api.meal.response;

import com.allermeal.application.meal.PersonalizedMealQueryResult;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PersonalizedMealQueryResponse(
	UUID childId,
	UUID schoolId,
	LocalDate rangeStart,
	LocalDate rangeEnd,
	String collectionStatus,
	Integer retryAfterSeconds,
	List<PersonalizedMealResponse> meals,
	List<PublicMealTargetResponse> pendingTargets
) {

	public static PersonalizedMealQueryResponse from(PersonalizedMealQueryResult result) {
		return new PersonalizedMealQueryResponse(
			result.childProfileId().value(),
			result.schoolId().value(),
			result.rangeStart(),
			result.rangeEnd(),
			result.collectionStatus().name(),
			result.retryAfterSeconds(),
			result.meals().stream().map(PersonalizedMealResponse::from).toList(),
			result.pendingTargets().stream().map(PublicMealTargetResponse::from).toList());
	}
}
