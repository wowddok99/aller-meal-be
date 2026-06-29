package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminExternalApiLogItemResult;
import com.allermeal.domain.meal.MealType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminExternalApiLogItemResponse(
	UUID externalApiLogId,
	String provider,
	String operation,
	UUID schoolId,
	LocalDate mealDate,
	MealType mealType,
	String method,
	String endpoint,
	Integer httpStatus,
	String outcome,
	String failureCode,
	Long responseTimeMillis,
	Instant createdAt
) {

	public static AdminExternalApiLogItemResponse from(AdminExternalApiLogItemResult result) {
		return new AdminExternalApiLogItemResponse(
			result.externalApiLogId(),
			result.provider(),
			result.operation(),
			result.schoolId().value(),
			result.mealDate(),
			result.mealType(),
			result.method(),
			result.endpoint(),
			result.httpStatus(),
			result.outcome(),
			result.failureCode(),
			result.responseTimeMillis(),
			result.createdAt());
	}
}
