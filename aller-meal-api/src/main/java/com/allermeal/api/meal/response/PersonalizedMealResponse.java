package com.allermeal.api.meal.response;

import com.allermeal.application.meal.PersonalizedMealRiskResult;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.meal.MealItemId;
import com.allermeal.domain.risk.MealItemRisk;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public record PersonalizedMealResponse(
	UUID mealId,
	LocalDate mealDate,
	String mealType,
	Instant sourceReceivedAt,
	String labelingStatus,
	String nutritionInfo,
	String originInfo,
	@Schema(nullable = true, description = "원문 전체를 해석할 수 없으면 null이며, originInfo를 표시해야 합니다.")
	List<MealOriginResponse> origins,
	String riskLevel,
	String riskVersion,
	List<PersonalizedMealItemResponse> items
) {

	public static PersonalizedMealResponse from(PersonalizedMealRiskResult result) {
		Meal meal = result.meal();
		Map<MealItemId, MealItemRisk> riskByItemId = result.risk().itemRisks().stream()
			.collect(Collectors.toMap(MealItemRisk::mealItemId, Function.identity()));
		return new PersonalizedMealResponse(
			meal.id().value(),
			meal.mealDate(),
			meal.mealType().name(),
			meal.sourceReceivedAt(),
			meal.labelingStatus().name(),
			meal.nutritionInfo(),
			meal.originInfo(),
			MealOriginResponse.parseAllOrNull(meal.originInfo()),
			result.risk().riskLevel().name(),
			result.risk().riskVersion(),
			meal.items().stream()
				.map(item -> PersonalizedMealItemResponse.from(item, riskByItemId.get(item.id())))
				.toList());
	}
}
