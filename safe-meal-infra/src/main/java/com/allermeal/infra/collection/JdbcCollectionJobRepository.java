package com.allermeal.infra.collection;

import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.ConcurrentStateChangeException;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.common.EntityTimestamps;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCollectionJobRepository implements CollectionJobRepository {

	private static final String RETURNING_COLUMNS = """
		collection_job_id, school_id, meal_date, meal_type, status, response_time_millis,
		collection_duration_millis, lease_until, raw_object_id,
		failure_code, failure_message, created_at, updated_at
		""";

	private final JdbcClient jdbcClient;

	public JdbcCollectionJobRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	@org.springframework.transaction.annotation.Transactional
	public CollectionJob createOrGetActive(CollectionJob job, java.time.Instant staleBefore) {
		if (job.status() != CollectionJobStatus.PENDING) {
			throw new IllegalArgumentException("PENDING 수집 작업만 중복 방지 생성할 수 있습니다.");
		}
		Objects.requireNonNull(staleBefore, "stale 기준 시각은 null일 수 없습니다.");
		jdbcClient.sql("""
				UPDATE collection_jobs SET
				    status = 'FAILED',
				    response_time_millis = 0,
				    collection_duration_millis = 0,
				    lease_until = NULL,
				    raw_object_id = NULL,
				    failure_code = 'LEASE_EXPIRED',
				    failure_message = '수집 작업 lease가 만료되었습니다.',
				    updated_at = :staleBefore
				WHERE school_id = :schoolId AND meal_date = :mealDate AND meal_type = :mealType
				  AND status = 'RUNNING' AND lease_until <= :staleBefore
				""")
			.param("schoolId", job.schoolId().value())
			.param("mealDate", job.mealDate())
			.param("mealType", job.mealType().name())
			.param("staleBefore", OffsetDateTime.ofInstant(staleBefore, ZoneOffset.UTC))
			.update();
		return jdbcClient.sql("""
				INSERT INTO collection_jobs (
				    collection_job_id, school_id, meal_date, meal_type, status, response_time_millis,
				    collection_duration_millis, lease_until, raw_object_id,
				    failure_code, failure_message, created_at, updated_at
				)
				VALUES (
				    :jobId, :schoolId, :mealDate, :mealType, :status, :responseTimeMillis,
				    :collectionDurationMillis, :leaseUntil, :rawObjectId,
				    :failureCode, :failureMessage, :createdAt, :updatedAt
				)
				ON CONFLICT (school_id, meal_date, meal_type) WHERE status IN ('PENDING', 'RUNNING')
				DO UPDATE SET updated_at = collection_jobs.updated_at
				RETURNING """ + " " + RETURNING_COLUMNS)
			.params(parameters(job))
			.query(this::map)
			.single();
	}

	@Override
	public CollectionJob save(CollectionJobStatus expectedStatus, CollectionJob job) {
		Objects.requireNonNull(expectedStatus, "기대 수집 작업 상태는 null일 수 없습니다.");
		return jdbcClient.sql("""
				UPDATE collection_jobs SET
				    status = :status,
				    response_time_millis = :responseTimeMillis,
				    collection_duration_millis = :collectionDurationMillis,
				    lease_until = :leaseUntil,
				    raw_object_id = :rawObjectId,
				    failure_code = :failureCode,
				    failure_message = :failureMessage,
				    updated_at = :updatedAt
				WHERE collection_job_id = :jobId AND status = :expectedStatus
				RETURNING """ + " " + RETURNING_COLUMNS)
			.params(parameters(job))
			.param("expectedStatus", expectedStatus.name())
			.query(this::map)
			.optional()
			.orElseThrow(() -> new ConcurrentStateChangeException(
				"수집 작업 상태가 이미 변경되어 저장할 수 없습니다."));
	}

	private Map<String, Object> parameters(CollectionJob job) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("jobId", job.id().value());
		parameters.put("schoolId", job.schoolId().value());
		parameters.put("mealDate", job.mealDate());
		parameters.put("mealType", job.mealType().name());
		parameters.put("status", job.status().name());
		parameters.put("responseTimeMillis", job.responseTimeMillis());
		parameters.put("collectionDurationMillis", job.collectionDurationMillis());
		parameters.put("leaseUntil", job.leaseUntil() == null
			? null : OffsetDateTime.ofInstant(job.leaseUntil(), ZoneOffset.UTC));
		parameters.put("rawObjectId", job.rawObjectId());
		parameters.put("failureCode", job.failureCode());
		parameters.put("failureMessage", job.failureMessage());
		parameters.put("createdAt", OffsetDateTime.ofInstant(job.timestamps().createdAt(), ZoneOffset.UTC));
		parameters.put("updatedAt", OffsetDateTime.ofInstant(job.timestamps().updatedAt(), ZoneOffset.UTC));
		return parameters;
	}

	private CollectionJob map(ResultSet resultSet, int rowNum) throws SQLException {
		return new CollectionJob(
			new CollectionJobId(resultSet.getObject("collection_job_id", UUID.class)),
			new SchoolId(resultSet.getObject("school_id", UUID.class)),
			resultSet.getObject("meal_date", LocalDate.class),
			MealType.valueOf(resultSet.getString("meal_type")),
			CollectionJobStatus.valueOf(resultSet.getString("status")),
			resultSet.getObject("response_time_millis", Long.class),
			resultSet.getObject("collection_duration_millis", Long.class),
			toInstant(resultSet.getObject("lease_until", OffsetDateTime.class)),
			resultSet.getObject("raw_object_id", UUID.class),
			resultSet.getString("failure_code"),
			resultSet.getString("failure_message"),
			new EntityTimestamps(
				resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
				resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()));
	}

	private java.time.Instant toInstant(OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}
}
