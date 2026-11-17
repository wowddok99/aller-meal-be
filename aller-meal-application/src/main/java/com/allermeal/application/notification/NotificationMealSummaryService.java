package com.allermeal.application.notification;

import com.allermeal.application.port.out.AllergenRepository;
import com.allermeal.application.port.out.ChildAllergenRepository;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.command.NotificationMealItemCommand;
import com.allermeal.application.port.out.result.NotificationMailContextResult;
import com.allermeal.application.port.out.result.MealQueryResult;
import com.allermeal.domain.allergen.Allergen;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.meal.MealItemId;
import com.allermeal.domain.meal.MealItemLabelingStatus;
import com.allermeal.domain.risk.ChildMealRisk;
import com.allermeal.domain.risk.ChildMealRiskCalculator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class NotificationMealSummaryService {

	private final MealRepository meals;
	private final ChildAllergenRepository childAllergens;
	private final AllergenRepository allergens;

	public NotificationMealSummaryService(MealRepository meals, ChildAllergenRepository childAllergens, AllergenRepository allergens) {
		this.meals = meals;
		this.childAllergens = childAllergens;
		this.allergens = allergens;
	}

	public List<NotificationMealItemCommand> summarize(NotificationMailContextResult context) {
		var target = context.target();
		if (target.riskVersion() == null || target.mealCount() == 0) return List.of();
		List<Meal> currentMeals = meals.findCollectedInRange(context.schoolId(), target.notificationDate(), target.notificationDate())
			.stream().map(MealQueryResult::meal).filter(Objects::nonNull).sorted(Comparator.comparing(Meal::mealType)).toList();
		if (currentMeals.size() != target.mealCount() || currentMeals.stream().anyMatch(meal ->
			!meal.schoolId().equals(context.schoolId()) || !meal.mealDate().equals(target.notificationDate()))) return List.of();
		var childCodes = childAllergens.findAllergenCodes(target.ownerId(), target.childProfileId());
		Map<MealItemId, List<Integer>> codes = meals.findAllergenCodesByMealIds(currentMeals.stream().map(Meal::id).toList());
		List<ChildMealRisk> risks = currentMeals.stream().map(meal -> ChildMealRiskCalculator.calculate(
			target.childProfileId(), childCodes, meal, meal.items().stream().collect(Collectors.toMap(
				item -> item.id(), item -> codes.getOrDefault(item.id(), List.of()))))).toList();
		var level = NotificationRiskSummary.aggregate(risks);
		if (level != target.riskLevel() || !NotificationRiskSummary.reason(level).name().equals(target.reason().name())
			|| !NotificationRiskSummary.aggregateRiskVersion(risks).equals(target.riskVersion())) return List.of();
		Map<Integer, String> names = allergens.findAll().stream().collect(Collectors.toMap(Allergen::code, Allergen::name));
		List<NotificationMealItemCommand> summary = new ArrayList<>();
		for (int index = 0; index < currentMeals.size(); index++) {
			var meal = currentMeals.get(index);
			var risksByItem = risks.get(index).itemRisks().stream().collect(Collectors.toMap(risk -> risk.mealItemId(), risk -> risk));
			for (var item : meal.items()) {
				if (item.labelingStatus() != MealItemLabelingStatus.LABELED) continue;
				var matched = risksByItem.get(item.id()).matchedAllergenCodes();
				if (matched.stream().anyMatch(code -> !names.containsKey(code))) return List.of();
				summary.add(new NotificationMealItemCommand(meal.mealType(), item.name(), matched.stream().map(names::get).toList()));
			}
		}
		return List.copyOf(summary);
	}
}
