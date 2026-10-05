package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminCollectionJobItemResult;
import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.meal.MealType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminCollectionJobItemResponse(
	UUID collectionJobId, UUID schoolId, String schoolName, LocalDate mealDate, MealType mealType,
	CollectionJobStatus status, Long responseTimeMillis, Long collectionDurationMillis, Instant leaseUntil,
	UUID rawObjectId, String failureCode, String failureMessage, Instant createdAt, Instant updatedAt,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean unresolvedFailure,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdminCollectionRecoveryResponse recovery,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdminCollectionAvailableActionsResponse availableActions
) {
	public static AdminCollectionJobItemResponse from(AdminCollectionJobItemResult result) {
		return new AdminCollectionJobItemResponse(result.collectionJobId().value(), result.schoolId().value(),
			result.schoolName(), result.mealDate(), result.mealType(), result.status(), result.responseTimeMillis(),
			result.collectionDurationMillis(), result.leaseUntil(), result.rawObjectId(), result.failureCode(),
			result.failureMessage(), result.createdAt(), result.updatedAt(), result.unresolvedFailure(),
			AdminCollectionRecoveryResponse.from(result.recovery()), AdminCollectionAvailableActionsResponse.from(result.availableActions()));
	}
}
