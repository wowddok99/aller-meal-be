package com.allermeal.application.port.out.command;

import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExternalApiLogCommand(
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
