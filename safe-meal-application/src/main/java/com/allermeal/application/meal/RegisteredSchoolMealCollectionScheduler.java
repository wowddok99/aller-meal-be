package com.allermeal.application.meal;

import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.MealCollectionDispatcher;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.SchoolCollectionSubscriptionRepository;
import com.allermeal.application.port.out.result.ActiveSchoolCollectionSubscriptionResult;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RegisteredSchoolMealCollectionScheduler {

	private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

	private final SchoolCollectionSubscriptionRepository subscriptionRepository;
	private final CollectionJobRepository collectionJobRepository;
	private final MealRepository mealRepository;
	private final MealCollectionDispatcher collectionDispatcher;
	private final Clock clock;

	public RegisteredSchoolMealCollectionScheduler(
		SchoolCollectionSubscriptionRepository subscriptionRepository,
		CollectionJobRepository collectionJobRepository,
		MealRepository mealRepository,
		MealCollectionDispatcher collectionDispatcher,
		Clock clock
	) {
		this.subscriptionRepository = subscriptionRepository;
		this.collectionJobRepository = collectionJobRepository;
		this.mealRepository = mealRepository;
		this.collectionDispatcher = collectionDispatcher;
		this.clock = clock;
	}

	public ScheduledMealCollectionResult collect(int daysAhead) {
		if (daysAhead < 1 || daysAhead > 31) {
			throw new IllegalArgumentException("수집 대상 일수는 1 이상 31 이하여야 합니다.");
		}
		List<ActiveSchoolCollectionSubscriptionResult> subscriptions = subscriptionRepository.findActiveSubscriptions();
		List<CollectionJob> requestedJobs = new ArrayList<>();
		int targetCount = 0;
		int skippedJobCount = 0;
		LocalDate startDate = LocalDate.now(clock.withZone(DEFAULT_ZONE));
		LocalDate endDate = startDate.plusDays(daysAhead - 1L);
		for (ActiveSchoolCollectionSubscriptionResult subscription : subscriptions) {
			SchoolId schoolId = subscription.schoolId();
			Set<PublicMealTarget> collectedTargets = new HashSet<>(mealRepository
				.findCollectedInRange(schoolId, startDate, endDate).stream()
				.map(result -> new PublicMealTarget(result.mealDate(), result.mealType()))
				.toList());
			for (int dayOffset = 0; dayOffset < daysAhead; dayOffset++) {
				LocalDate mealDate = startDate.plusDays(dayOffset);
				for (MealType mealType : MealType.values()) {
					targetCount++;
					if (collectedTargets.contains(new PublicMealTarget(mealDate, mealType))) {
						skippedJobCount++;
						continue;
					}
					RequestedCollectionJob requested = requestJob(schoolId, mealDate, mealType);
					if (requested.created()) {
						requestedJobs.add(requested.job());
					} else {
						skippedJobCount++;
					}
				}
			}
		}
		requestedJobs.forEach(collectionDispatcher::dispatch);
		return new ScheduledMealCollectionResult(
			subscriptions.size(), targetCount, requestedJobs.size(), skippedJobCount);
	}

	private RequestedCollectionJob requestJob(SchoolId schoolId, LocalDate mealDate, MealType mealType) {
		CollectionJob pending = CollectionJob.pending(
			new CollectionJobId(UUID.randomUUID()), schoolId, mealDate, mealType, clock.instant());
		CollectionJob active = collectionJobRepository.createOrGetActive(pending, clock.instant());
		return new RequestedCollectionJob(active, active.status() == CollectionJobStatus.PENDING
			&& active.id().equals(pending.id()));
	}

	private record RequestedCollectionJob(CollectionJob job, boolean created) {
	}
}
