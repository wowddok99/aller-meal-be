package com.allermeal.application.admin;

import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminExternalApiLogItemResult(
	UUID externalApiLogId,
	String provider,
	String operation,
	SchoolId schoolId,
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
}
