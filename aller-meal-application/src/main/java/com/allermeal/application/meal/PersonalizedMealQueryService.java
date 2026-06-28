package com.allermeal.application.meal;

import com.allermeal.application.child.ChildProfileNotFoundException;
import com.allermeal.application.port.out.ChildAllergenRepository;
import com.allermeal.application.port.out.ChildProfileRepository;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.domain.child.ChildProfile;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.meal.MealId;
import com.allermeal.domain.meal.MealItemId;
import com.allermeal.domain.risk.ChildMealRiskCalculator;
import com.allermeal.domain.user.UserId;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class PersonalizedMealQueryService {

	private static final ZoneId DEFAULT_TODAY_ZONE = ZoneId.of("Asia/Seoul");

	private final ChildProfileRepository childProfileRepository;
	private final ChildAllergenRepository childAllergenRepository;
	private final MealRepository mealRepository;
	private final PublicMealQueryService publicMealQueryService;
	private final Clock clock;

	public PersonalizedMealQueryService(
		ChildProfileRepository childProfileRepository,
		ChildAllergenRepository childAllergenRepository,
		MealRepository mealRepository,
		PublicMealQueryService publicMealQueryService,
		Clock clock
	) {
		this.childProfileRepository = childProfileRepository;
		this.childAllergenRepository = childAllergenRepository;
		this.mealRepository = mealRepository;
		this.publicMealQueryService = publicMealQueryService;
		this.clock = clock;
	}

	public PersonalizedMealQueryResult findToday(UserId ownerId, ChildProfileId childProfileId) {
		return findDaily(ownerId, childProfileId, LocalDate.now(clock.withZone(DEFAULT_TODAY_ZONE)));
	}

	public PersonalizedMealQueryResult findDaily(UserId ownerId, ChildProfileId childProfileId, LocalDate date) {
		Objects.requireNonNull(date, "조회 날짜는 null일 수 없습니다.");
		return findRange(ownerId, childProfileId, date, date);
	}

	public PersonalizedMealQueryResult findWeekly(UserId ownerId, ChildProfileId childProfileId, LocalDate date) {
		Objects.requireNonNull(date, "조회 기준일은 null일 수 없습니다.");
		LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		return findRange(ownerId, childProfileId, start, start.plusDays(6));
	}

	private PersonalizedMealQueryResult findRange(
		UserId ownerId,
		ChildProfileId childProfileId,
		LocalDate startDate,
		LocalDate endDate
	) {
		ChildProfile childProfile = childProfileRepository.findByIdAndOwnerId(childProfileId, ownerId)
			.orElseThrow(ChildProfileNotFoundException::new);
		PublicMealQueryResult publicResult = startDate.equals(endDate)
			? publicMealQueryService.findDaily(childProfile.schoolId(), startDate)
			: publicMealQueryService.findWeekly(childProfile.schoolId(), startDate);
		List<Integer> childAllergens = childAllergenRepository.findAllergenCodes(ownerId, childProfileId);
		Map<MealItemId, List<Integer>> allergenCodes = mealRepository.findAllergenCodesByMealIds(publicResult.meals().stream()
			.map(Meal::id)
			.toList());
		List<PersonalizedMealRiskResult> meals = publicResult.meals().stream()
			.map(meal -> new PersonalizedMealRiskResult(
				meal,
				ChildMealRiskCalculator.calculate(childProfileId, childAllergens, meal, allergenCodesForMeal(meal, allergenCodes))))
			.collect(Collectors.toList());
		return new PersonalizedMealQueryResult(
			childProfileId,
			childProfile.schoolId(),
			publicResult.rangeStart(),
			publicResult.rangeEnd(),
			publicResult.collectionStatus(),
			publicResult.retryAfterSeconds(),
			meals,
			publicResult.pendingTargets());
	}

	private Map<MealItemId, List<Integer>> allergenCodesForMeal(Meal meal, Map<MealItemId, List<Integer>> allergenCodes) {
		return meal.items().stream()
			.collect(Collectors.toMap(
				item -> item.id(),
				item -> allergenCodes.getOrDefault(item.id(), List.of())));
	}
}
