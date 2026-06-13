package com.allermeal.application.meal;

import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

public final class MealCollectionEntrypoint {

	private final CollectionJobRepository collectionJobRepository;
	private final MealCollectionService collectionService;
	private final Clock clock;

	public MealCollectionEntrypoint(
		CollectionJobRepository collectionJobRepository,
		MealCollectionService collectionService,
		Clock clock
	) {
		this.collectionJobRepository = collectionJobRepository;
		this.collectionService = collectionService;
		this.clock = clock;
	}

	public MealCollectionService.CollectionResult collect(SchoolId schoolId, LocalDate mealDate, MealType mealType) {
		CollectionJob pending = CollectionJob.pending(
			new CollectionJobId(UUID.randomUUID()), schoolId, mealDate, mealType, clock.instant());
		CollectionJob active = collectionJobRepository.createOrGetActive(pending, clock.instant());
		if (active.status() != com.allermeal.domain.collection.CollectionJobStatus.PENDING) {
			throw new MealCollectionException("COLLECTION_ALREADY_RUNNING", "동일 대상 수집 작업이 이미 실행 중입니다.");
		}
		return collectionService.collect(active);
	}
}
