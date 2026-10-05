package com.allermeal.infra.admin;

import com.allermeal.application.admin.AdminCollectionRecoveryResult;
import com.allermeal.application.admin.AdminCollectionRecoveryStatus;
import com.allermeal.application.admin.AdminCollectionRequestType;
import com.allermeal.application.port.out.AdminRecollectionRequestRepository;
import com.allermeal.application.port.out.command.AdminRecollectionRequestCommand;
import com.allermeal.application.port.out.result.AdminCollectionRecoveryLookupResult;
import com.allermeal.application.port.out.result.AdminRecollectionRequestResult;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.user.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminRecollectionRequestRepository implements AdminRecollectionRequestRepository {

	private final JdbcClient jdbcClient;

	public JdbcAdminRecollectionRequestRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	@org.springframework.transaction.annotation.Transactional
	public AdminRecollectionRequestResult save(AdminRecollectionRequestCommand command) {
		CollectionDashboardSnapshotInvalidator.invalidate(jdbcClient);
		AdminRecollectionRequestResult result = jdbcClient.sql("""
				INSERT INTO admin_recollection_requests (
				    recollection_request_id, idempotency_key, actor_user_id,
				    original_collection_job_id, collection_job_id, created_at, request_type
				)
				VALUES (
				    :requestId, :idempotencyKey, :actorUserId,
				    :originalCollectionJobId, :collectionJobId, :createdAt, :requestType
				)
				ON CONFLICT (idempotency_key) DO NOTHING
				RETURNING collection_job_id, false AS duplicate, false AS conflict
				""")
			.param("requestType", command.requestType().name())
			.param("requestId", command.recollectionRequestId())
			.param("idempotencyKey", command.idempotencyKey())
			.param("actorUserId", command.actorUserId().value())
			.param("originalCollectionJobId", command.originalCollectionJobId().value())
			.param("collectionJobId", command.collectionJobId().value())
			.param("createdAt", Timestamp.from(command.createdAt()))
			.query(this::mapResult)
			.optional()
			.orElse(null);
		if (result != null) {
			return result;
		}
		return findByIdempotencyKey(command.idempotencyKey(), command.originalCollectionJobId(), command.actorUserId(), command.requestType())
			.orElseThrow(() -> new IllegalStateException("멱등 요청 저장 후 조회 실패"));
	}

	@Override
	public Optional<AdminRecollectionRequestResult> findByIdempotencyKey(
		String idempotencyKey,
		CollectionJobId originalCollectionJobId
	) {
		return jdbcClient.sql("""
				SELECT collection_job_id,
				       true AS duplicate,
				       original_collection_job_id <> :originalCollectionJobId AS conflict
				FROM admin_recollection_requests
				WHERE idempotency_key = :idempotencyKey
				""")
			.param("idempotencyKey", idempotencyKey)
			.param("originalCollectionJobId", originalCollectionJobId.value())
			.query(this::mapResult)
			.optional();
	}

	@Override
	public void lockRequestKey(String key) {
		jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 7404621065))")
			.param("key", key).query((rs, row) -> true).single();
	}

	@Override
	public void lockCollectionTarget(CollectionJob job) {
		jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 7404621066))")
			.param("key", job.schoolId().value() + "/" + job.mealDate() + "/" + job.mealType())
			.query((rs, row) -> true).single();
		// 수집 상태 저장과 동일하게 snapshot 잠금을 원본 행 잠금보다 먼저 획득합니다.
		CollectionDashboardSnapshotInvalidator.invalidate(jdbcClient);
	}

	@Override
	public Optional<AdminRecollectionRequestResult> findByIdempotencyKey(String key, CollectionJobId source,
		UserId actor, AdminCollectionRequestType type) {
		return jdbcClient.sql("""
			SELECT collection_job_id, true AS duplicate,
				(original_collection_job_id <> :source OR actor_user_id <> :actor OR request_type <> :type) AS conflict
			FROM admin_recollection_requests WHERE idempotency_key = :key
			""").param("key", key).param("source", source.value()).param("actor", actor.value())
			.param("type", type.name()).query(this::mapResult).optional();
	}

	@Override
	public Map<UUID, AdminCollectionRecoveryLookupResult> findRecoveries(Collection<UUID> jobIds) {
		return findRecoveries(jobIds, Instant.now());
	}

	@Override
	public Map<UUID, AdminCollectionRecoveryLookupResult> findRecoveries(Collection<UUID> jobIds, Instant now) {
		if (jobIds.isEmpty()) return Map.of();
		return jdbcClient.sql("""
			SELECT summary.*,
				coalesce(leases.has_pending OR leases.latest_running_lease_until > :now, false) AS has_live_active
			FROM collection_job_recovery_summaries summary
			LEFT JOIN collection_job_recovery_active_leases leases ON leases.collection_job_id = summary.collection_job_id
			WHERE summary.collection_job_id IN (:ids)
			""").param("ids", jobIds).param("now", Timestamp.from(now))
			.query((rs, row) -> Map.entry(rs.getObject("collection_job_id", UUID.class),
				new AdminCollectionRecoveryLookupResult(rs.getBoolean("unresolved_failure"), rs.getBoolean("has_live_active"),
					instant(rs, "latest_execution_at"), new AdminCollectionRecoveryResult(
						AdminCollectionRecoveryStatus.valueOf(rs.getString("recovery_status")),
						rs.getObject("latest_collection_job_id", UUID.class),
						rs.getString("latest_status") == null ? null : CollectionJobStatus.valueOf(rs.getString("latest_status")),
						instant(rs, "requested_at"), instant(rs, "completed_at"),
						rs.getObject("resolved_collection_job_id", UUID.class), instant(rs, "resolved_at")))))
			.list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	private AdminRecollectionRequestResult mapResult(ResultSet resultSet, int rowNum) throws SQLException {
		return new AdminRecollectionRequestResult(
			new CollectionJobId(resultSet.getObject("collection_job_id", UUID.class)),
			resultSet.getBoolean("duplicate"),
			resultSet.getBoolean("conflict"));
	}
}
