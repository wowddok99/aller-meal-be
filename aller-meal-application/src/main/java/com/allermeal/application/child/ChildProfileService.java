package com.allermeal.application.child;

import com.allermeal.application.port.out.ChildProfileRepository;
import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.MealCollectionDispatcher;
import com.allermeal.application.port.out.SchoolRepository;
import com.allermeal.application.port.out.SchoolCollectionSubscriptionRepository;
import com.allermeal.application.port.out.command.RegisterSchoolCollectionSubscriptionCommand;
import com.allermeal.application.port.out.command.UnregisterSchoolCollectionSubscriptionCommand;
import com.allermeal.application.port.out.result.SchoolCollectionSubscriptionActivationResult;
import com.allermeal.application.school.SchoolNotFoundException;
import com.allermeal.domain.child.ChildProfile;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import com.allermeal.domain.user.UserId;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class ChildProfileService {

	private final ChildProfileRepository childProfileRepository;
	private final SchoolRepository schoolRepository;
	private final SchoolCollectionSubscriptionRepository subscriptionRepository;
	private final CollectionJobRepository collectionJobRepository;
	private final MealCollectionDispatcher collectionDispatcher;
	private final Clock clock;

	public ChildProfileService(
		ChildProfileRepository childProfileRepository,
		SchoolRepository schoolRepository,
		SchoolCollectionSubscriptionRepository subscriptionRepository,
		CollectionJobRepository collectionJobRepository,
		MealCollectionDispatcher collectionDispatcher,
		Clock clock
	) {
		this.childProfileRepository = childProfileRepository;
		this.schoolRepository = schoolRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.collectionJobRepository = collectionJobRepository;
		this.collectionDispatcher = collectionDispatcher;
		this.clock = clock;
	}

	@Transactional
	public ChildProfile create(UserId ownerId, CreateChildProfileCommand command) {
		requireSchool(command.schoolId());
		try {
			Instant changedAt = clock.instant();
			ChildProfile childProfile = childProfileRepository.save(ChildProfile.create(new ChildProfileId(UUID.randomUUID()), ownerId,
				command.name(), command.grade(), command.classNumber(), command.schoolId(), changedAt));
			requestInitialCollectionIfFirstRegisteredChild(command.schoolId(), changedAt);
			return childProfile;
		} catch (IllegalArgumentException exception) {
			throw new InvalidChildProfileRequestException();
		}
	}

	public List<ChildProfile> findAll(UserId ownerId) { return childProfileRepository.findAllByOwnerId(ownerId); }

	public ChildProfile find(UserId ownerId, ChildProfileId childProfileId) {
		return childProfileRepository.findByIdAndOwnerId(childProfileId, ownerId).orElseThrow(ChildProfileNotFoundException::new);
	}

	@Transactional
	public ChildProfile update(UserId ownerId, ChildProfileId childProfileId, UpdateChildProfileCommand command) {
		ChildProfile childProfile = find(ownerId, childProfileId);
		requireSchool(command.schoolId());
		try {
			Instant changedAt = clock.instant();
			ChildProfile updatedChild = childProfileRepository.save(
				childProfile.update(command.name(), command.grade(), command.classNumber(), command.schoolId(), changedAt));
			if (!childProfile.schoolId().equals(command.schoolId())) {
				unregisterChild(childProfile.schoolId(), changedAt);
				requestInitialCollectionIfFirstRegisteredChild(command.schoolId(), changedAt);
			}
			return updatedChild;
		} catch (IllegalArgumentException exception) {
			throw new InvalidChildProfileRequestException();
		}
	}

	@Transactional
	public void delete(UserId ownerId, ChildProfileId childProfileId) {
		ChildProfile childProfile = find(ownerId, childProfileId);
		if (!childProfileRepository.deleteByIdAndOwnerId(childProfileId, ownerId)) throw new ChildProfileNotFoundException();
		unregisterChild(childProfile.schoolId(), clock.instant());
	}

	private void requireSchool(SchoolId schoolId) {
		if (schoolRepository.findById(schoolId).isEmpty()) throw new SchoolNotFoundException();
	}

	private void requestInitialCollectionIfFirstRegisteredChild(SchoolId schoolId, Instant changedAt) {
		SchoolCollectionSubscriptionActivationResult activation = subscriptionRepository.registerChild(
			new RegisterSchoolCollectionSubscriptionCommand(schoolId, changedAt));
		if (!activation.firstRegisteredChild()) return;
		List<CollectionJob> requestedJobs = requestCurrentWeekCollection(schoolId, changedAt);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				requestedJobs.forEach(collectionDispatcher::dispatch);
			}
		});
	}

	private List<CollectionJob> requestCurrentWeekCollection(SchoolId schoolId, Instant requestedAt) {
		LocalDate weekStart = LocalDate.ofInstant(requestedAt, ZoneId.of("Asia/Seoul"))
			.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		List<CollectionJob> requestedJobs = new ArrayList<>();
		for (LocalDate mealDate = weekStart; !mealDate.isAfter(weekStart.plusDays(6)); mealDate = mealDate.plusDays(1)) {
			for (MealType mealType : MealType.values()) {
				CollectionJob job = CollectionJob.pending(
					new CollectionJobId(UUID.randomUUID()), schoolId, mealDate, mealType, requestedAt);
				CollectionJob active = collectionJobRepository.createOrGetActive(job, requestedAt);
				if (active.status() == CollectionJobStatus.PENDING) requestedJobs.add(active);
			}
		}
		return requestedJobs;
	}

	private void unregisterChild(SchoolId schoolId, Instant changedAt) {
		subscriptionRepository.unregisterChild(new UnregisterSchoolCollectionSubscriptionCommand(
			schoolId, changedAt, changedAt.plus(java.time.Duration.ofDays(30))));
	}
}
