package com.allermeal.application.meal;

import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.MealCollectionDispatcher;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.SchoolRepository;
import com.allermeal.application.port.out.result.MealQueryResult;
import com.allermeal.application.school.SchoolNotFoundException;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class PublicMealQueryService {

	public static final int RETRY_AFTER_SECONDS = 3;

	private final SchoolRepository schoolRepository;
	private final MealRepository mealRepository;
	private final CollectionJobRepository collectionJobRepository;
	private final MealCollectionDispatcher collectionDispatcher;
	private final Clock clock;

	public PublicMealQueryService(
		SchoolRepository schoolRepository,
		MealRepository mealRepository,
		CollectionJobRepository collectionJobRepository,
		MealCollectionDispatcher collectionDispatcher,
		Clock clock
	) {
		this.schoolRepository = schoolRepository;
		this.mealRepository = mealRepository;
		this.collectionJobRepository = collectionJobRepository;
		this.collectionDispatcher = collectionDispatcher;
		this.clock = clock;
	}

	public PublicMealQueryResult findDaily(SchoolId schoolId, LocalDate date) {
		return findRange(schoolId, date, date);
	}

	public PublicMealQueryResult findWeekly(SchoolId schoolId, LocalDate date) {
		LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		return findRange(schoolId, start, start.plusDays(6));
	}

	private PublicMealQueryResult findRange(SchoolId schoolId, LocalDate startDate, LocalDate endDate) {
		schoolRepository.findById(schoolId).orElseThrow(SchoolNotFoundException::new);
		List<MealQueryResult> collected = mealRepository.findCollectedInRange(schoolId, startDate, endDate);
		Set<PublicMealTarget> collectedTargets = new HashSet<>(collected.stream()
			.map(result -> new PublicMealTarget(result.mealDate(), result.mealType()))
			.toList());
		List<PublicMealTarget> pendingTargets = new ArrayList<>();
		for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
			for (MealType mealType : MealType.values()) {
				PublicMealTarget target = new PublicMealTarget(date, mealType);
				if (!collectedTargets.contains(target)) {
					pendingTargets.add(target);
					requestCollection(schoolId, target);
				}
			}
		}
		List<Meal> meals = collected.stream()
			.map(MealQueryResult::meal)
			.filter(Objects::nonNull)
			.sorted(Comparator.comparing(Meal::mealDate).thenComparing(Meal::mealType))
			.toList();
		boolean ready = pendingTargets.isEmpty();
		return new PublicMealQueryResult(
			schoolId, startDate, endDate,
			ready ? PublicMealCollectionStatus.READY : PublicMealCollectionStatus.COLLECTING,
			ready ? null : RETRY_AFTER_SECONDS,
			meals,
			pendingTargets);
	}

	private void requestCollection(SchoolId schoolId, PublicMealTarget target) {
		CollectionJob pending = CollectionJob.pending(
			new CollectionJobId(UUID.randomUUID()), schoolId, target.mealDate(), target.mealType(), clock.instant());
		CollectionJob active = collectionJobRepository.createOrGetActive(pending, clock.instant());
		if (active.status() == CollectionJobStatus.PENDING) {
			collectionDispatcher.dispatch(active);
		}
	}
}
