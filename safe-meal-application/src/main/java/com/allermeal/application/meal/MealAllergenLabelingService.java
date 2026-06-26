package com.allermeal.application.meal;

import com.allermeal.application.port.out.AllergenRepository;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.OutboxEventRepository;
import com.allermeal.application.port.out.PublicMealQueryCache;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.meal.MealId;
import com.allermeal.domain.meal.MealItem;
import com.allermeal.domain.meal.MealItemId;
import com.allermeal.domain.meal.MealItemLabelingStatus;
import com.allermeal.domain.meal.MealLabelingStatus;
import com.allermeal.domain.outbox.OutboxEvent;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

public class MealAllergenLabelingService {

	private final MealRepository mealRepository;
	private final AllergenRepository allergenRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final PublicMealQueryCache publicMealQueryCache;
	private final NeisAllergenLabelParser parser;
	private final Clock clock;

	public MealAllergenLabelingService(
		MealRepository mealRepository,
		AllergenRepository allergenRepository,
		OutboxEventRepository outboxEventRepository,
		PublicMealQueryCache publicMealQueryCache,
		NeisAllergenLabelParser parser,
		Clock clock
	) {
		this.mealRepository = mealRepository;
		this.allergenRepository = allergenRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.publicMealQueryCache = publicMealQueryCache;
		this.parser = parser;
		this.clock = clock;
	}

	@Transactional
	public boolean label(MealId mealId) {
		Meal meal = mealRepository.findById(mealId).orElse(null);
		if (meal == null || meal.labelingStatus() != MealLabelingStatus.PENDING) {
			return false;
		}
		Set<Integer> validCodes = allergenRepository.findAll().stream()
			.map(allergen -> allergen.code())
			.collect(Collectors.toUnmodifiableSet());
		Map<MealItemId, List<Integer>> labels = new HashMap<>();
		List<MealItem> items = meal.items().stream()
			.map(item -> labelItem(item, labels, validCodes))
			.toList();
		Meal labeledMeal = new Meal(
			meal.id(), meal.schoolId(), meal.mealDate(), meal.mealType(), meal.sourceVersion(), meal.sourceReceivedAt(),
			aggregate(items), meal.nutritionInfo(), meal.originInfo(), items);
		if (!mealRepository.saveAllergenLabels(labeledMeal, labels)) {
			return false;
		}
		outboxEventRepository.save(OutboxEvent.pending(
			UUID.randomUUID(),
			MealLabelingEvents.MEAL_LABELED,
			MealLabelingEvents.mealLabeledPayload(labeledMeal),
			clock.instant()));
		publicMealQueryCache.evictDailyAndWeekly(labeledMeal.schoolId(), labeledMeal.mealDate());
		return true;
	}

	private MealItem labelItem(MealItem item, Map<MealItemId, List<Integer>> labels, Set<Integer> validCodes) {
		NeisAllergenLabelParseResult result = parser.parse(item.rawText());
		MealItemLabelingStatus status = switch (result.status()) {
			case LABELED -> validCodes.containsAll(result.allergenCodes())
				? MealItemLabelingStatus.LABELED
				: MealItemLabelingStatus.LABELING_FAILED;
			case UNKNOWN -> MealItemLabelingStatus.UNKNOWN;
			case LABELING_FAILED -> MealItemLabelingStatus.LABELING_FAILED;
		};
		labels.put(item.id(), status == MealItemLabelingStatus.LABELED ? result.allergenCodes() : List.of());
		return new MealItem(item.id(), item.name(), item.rawText(), item.displayOrder(), status);
	}

	private MealLabelingStatus aggregate(List<MealItem> items) {
		boolean hasFailed = items.stream().anyMatch(item -> item.labelingStatus() == MealItemLabelingStatus.LABELING_FAILED);
		boolean hasUnknown = items.stream().anyMatch(item -> item.labelingStatus() == MealItemLabelingStatus.UNKNOWN);
		if (hasFailed) {
			return MealLabelingStatus.LABELING_FAILED;
		}
		if (hasUnknown) {
			return MealLabelingStatus.UNKNOWN;
		}
		return MealLabelingStatus.LABELED;
	}
}
