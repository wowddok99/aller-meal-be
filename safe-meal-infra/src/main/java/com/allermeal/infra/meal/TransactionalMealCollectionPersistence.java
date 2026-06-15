package com.allermeal.infra.meal;

import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.MealCollectionPersistence;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.result.MealCollectionCompletionResult;
import com.allermeal.application.port.out.result.MealSaveResult;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.raw.RawObjectMetadata;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalMealCollectionPersistence implements MealCollectionPersistence {

	private final JdbcClient jdbcClient;
	private final MealRepository mealRepository;
	private final CollectionJobRepository collectionJobRepository;

	public TransactionalMealCollectionPersistence(
		JdbcClient jdbcClient,
		MealRepository mealRepository,
		CollectionJobRepository collectionJobRepository
	) {
		this.jdbcClient = jdbcClient;
		this.mealRepository = mealRepository;
		this.collectionJobRepository = collectionJobRepository;
	}

	@Override
	@Transactional
	public MealCollectionCompletionResult complete(
		CollectionJob runningJob,
		CollectionJob succeededJob,
		RawObjectMetadata rawObject,
		List<Meal> meals
	) {
		if (!succeededJob.rawObjectId().equals(rawObject.id())) {
			throw new IllegalArgumentException("성공 작업과 원본 객체 ID가 일치하지 않습니다.");
		}
		boolean applied = updateCollectionVersion(runningJob, rawObject, !meals.isEmpty());
		List<MealSaveResult> savedMeals;
		if (!applied) {
			savedMeals = mealRepository.findByNaturalKey(
					runningJob.schoolId(), runningJob.mealDate(), runningJob.mealType())
				.map(meal -> List.of(new MealSaveResult(meal, false)))
				.orElseGet(List::of);
		} else if (meals.isEmpty()) {
			deleteMeal(runningJob, rawObject);
			savedMeals = List.of();
		} else {
			savedMeals = meals.stream().map(mealRepository::save).toList();
		}
		CollectionJob completed = collectionJobRepository.save(CollectionJobStatus.RUNNING, succeededJob);
		return new MealCollectionCompletionResult(completed, savedMeals);
	}

	private boolean updateCollectionVersion(CollectionJob job, RawObjectMetadata rawObject, boolean hasMeal) {
		boolean watermarkAdvanced = jdbcClient.sql("""
				UPDATE meal_collection_versions
				SET source_received_at = :sourceReceivedAt,
				    updated_at = CURRENT_TIMESTAMP
				WHERE school_id = :schoolId AND meal_date = :mealDate AND meal_type = :mealType
				  AND source_version = :sourceVersion
				  AND source_received_at < :sourceReceivedAt
				RETURNING school_id
				""")
			.param("schoolId", job.schoolId().value())
			.param("mealDate", job.mealDate())
			.param("mealType", job.mealType().name())
			.param("sourceVersion", rawObject.sha256Hash())
			.param("sourceReceivedAt", OffsetDateTime.ofInstant(rawObject.receivedAt(), ZoneOffset.UTC))
			.query(java.util.UUID.class)
			.optional()
			.isPresent();
		if (watermarkAdvanced) {
			return false;
		}
		return jdbcClient.sql("""
				INSERT INTO meal_collection_versions (
				    school_id, meal_date, meal_type, source_version, source_received_at, has_meal,
				    created_at, updated_at
				)
				VALUES (
				    :schoolId, :mealDate, :mealType, :sourceVersion, :sourceReceivedAt, :hasMeal,
				    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
				)
				ON CONFLICT (school_id, meal_date, meal_type) DO UPDATE SET
				    source_version = EXCLUDED.source_version,
				    source_received_at = EXCLUDED.source_received_at,
				    has_meal = EXCLUDED.has_meal,
				    updated_at = CURRENT_TIMESTAMP
				WHERE meal_collection_versions.source_received_at < EXCLUDED.source_received_at
				  AND meal_collection_versions.source_version <> EXCLUDED.source_version
				RETURNING school_id
				""")
			.param("schoolId", job.schoolId().value())
			.param("mealDate", job.mealDate())
			.param("mealType", job.mealType().name())
			.param("sourceVersion", rawObject.sha256Hash())
			.param("sourceReceivedAt", OffsetDateTime.ofInstant(rawObject.receivedAt(), ZoneOffset.UTC))
			.param("hasMeal", hasMeal)
			.query(java.util.UUID.class)
			.optional()
			.isPresent();
	}

	private void deleteMeal(CollectionJob job, RawObjectMetadata rawObject) {
		jdbcClient.sql("""
				DELETE FROM meals
				WHERE school_id = :schoolId AND meal_date = :mealDate AND meal_type = :mealType
				  AND source_received_at < :sourceReceivedAt
				""")
			.param("schoolId", job.schoolId().value())
			.param("mealDate", job.mealDate())
			.param("mealType", job.mealType().name())
			.param("sourceReceivedAt", OffsetDateTime.ofInstant(rawObject.receivedAt(), ZoneOffset.UTC))
			.update();
	}
}
