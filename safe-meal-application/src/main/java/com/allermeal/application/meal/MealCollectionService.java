package com.allermeal.application.meal;

import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.MealCollectionPersistence;
import com.allermeal.application.port.out.NeisMealClient;
import com.allermeal.application.port.out.NeisMealNormalizer;
import com.allermeal.application.port.out.RawPayloadStorage;
import com.allermeal.application.port.out.SchoolRepository;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.raw.RawObjectMetadata;
import com.allermeal.domain.school.School;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class MealCollectionService {

	private static final Duration RAW_RETENTION = Duration.ofDays(90);

	private final SchoolRepository schoolRepository;
	private final CollectionJobRepository collectionJobRepository;
	private final NeisMealClient neisMealClient;
	private final RawPayloadStorage rawPayloadStorage;
	private final NeisMealNormalizer normalizer;
	private final MealCollectionPersistence persistence;
	private final Clock clock;
	private final Duration leaseDuration;

	public MealCollectionService(
		SchoolRepository schoolRepository,
		CollectionJobRepository collectionJobRepository,
		NeisMealClient neisMealClient,
		RawPayloadStorage rawPayloadStorage,
		NeisMealNormalizer normalizer,
		MealCollectionPersistence persistence,
		Clock clock,
		Duration leaseDuration
	) {
		this.schoolRepository = schoolRepository;
		this.collectionJobRepository = collectionJobRepository;
		this.neisMealClient = neisMealClient;
		this.rawPayloadStorage = rawPayloadStorage;
		this.normalizer = normalizer;
		this.persistence = persistence;
		this.clock = clock;
		this.leaseDuration = leaseDuration;
	}

	public CollectionResult collect(CollectionJob pendingJob) {
		Instant collectionStartedAt = clock.instant();
		CollectionJob runningJob = collectionJobRepository.save(
			CollectionJobStatus.PENDING,
			pendingJob.start(collectionStartedAt, collectionStartedAt.plus(leaseDuration)));
		long responseTimeMillis = 0;
		RawObjectMetadata metadata = null;
		try {
			School school = schoolRepository.findById(runningJob.schoolId())
				.orElseThrow(() -> new MealCollectionException("SCHOOL_NOT_FOUND", "수집 대상 학교를 찾을 수 없습니다."));
			long fetchStartedNanos = System.nanoTime();
			NeisMealClient.RawMealResponse response;
			try {
				response = neisMealClient.fetch(school, runningJob.mealDate(), runningJob.mealType());
			} finally {
				responseTimeMillis = elapsedMillis(fetchStartedNanos);
			}
			try {
				metadata = rawPayloadStorage.store(new RawPayloadStorage.RawPayload(
					"neis-meal", response.bytes(), "application/json", response.receivedAt(),
					response.receivedAt().plus(RAW_RETENTION)));
			} catch (RuntimeException internalStorageFailure) {
				neisMealClient.recordExternalCallSuccess();
				throw internalStorageFailure;
			}
			List<Meal> meals;
			try {
				meals = normalizer.normalize(
					school, runningJob.mealDate(), runningJob.mealType(), response.bytes(), metadata);
			} catch (RuntimeException validationFailure) {
				neisMealClient.recordValidationFailure(validationFailure);
				throw validationFailure;
			}
			neisMealClient.recordValidationSuccess();
			Instant completedAt = clock.instant();
			CollectionJob succeeded = runningJob.succeed(
				responseTimeMillis,
				Duration.between(collectionStartedAt, completedAt).toMillis(),
				metadata.id(),
				completedAt);
			MealCollectionPersistence.CompletionResult completion = persistence.complete(
				runningJob, succeeded, metadata, meals);
			return new CollectionResult(completion.collectionJob(), metadata, completion.meals());
		} catch (RuntimeException exception) {
			MealCollectionException failure = toCollectionException(exception);
			try {
				Instant failedAt = clock.instant();
				collectionJobRepository.save(
					CollectionJobStatus.RUNNING,
					runningJob.fail(
						responseTimeMillis,
						Duration.between(collectionStartedAt, failedAt).toMillis(),
						metadata == null ? null : metadata.id(),
						failure.code(),
						failure.getMessage(),
						failedAt));
			} catch (RuntimeException saveFailure) {
				failure.addSuppressed(saveFailure);
			}
			throw failure;
		}
	}

	private long elapsedMillis(long startedNanos) {
		return Math.max(0, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
	}

	private MealCollectionException toCollectionException(RuntimeException exception) {
		if (exception instanceof MealCollectionException mealCollectionException) {
			return MealCollectionException.normalized(
				mealCollectionException.code(), mealCollectionException.getMessage(), mealCollectionException);
		}
		return MealCollectionException.normalized("COLLECTION_FAILED", exception.getMessage(), exception);
	}

	public record CollectionResult(
		CollectionJob collectionJob,
		RawObjectMetadata rawObject,
		List<MealRepository.MealSaveResult> meals
	) {

		public CollectionResult {
			meals = List.copyOf(meals);
		}
	}
}
