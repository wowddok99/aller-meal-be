package com.allermeal.infra.admin;

import com.allermeal.application.port.out.AdminRecollectionRequestRepository;
import com.allermeal.application.port.out.command.AdminRecollectionRequestCommand;
import com.allermeal.application.port.out.result.AdminRecollectionRequestResult;
import com.allermeal.domain.collection.CollectionJobId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminRecollectionRequestRepository implements AdminRecollectionRequestRepository {

	private final JdbcClient jdbcClient;

	public JdbcAdminRecollectionRequestRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public AdminRecollectionRequestResult save(AdminRecollectionRequestCommand command) {
		AdminRecollectionRequestResult result = jdbcClient.sql("""
				INSERT INTO admin_recollection_requests (
				    recollection_request_id, idempotency_key, actor_user_id,
				    original_collection_job_id, collection_job_id, created_at
				)
				VALUES (
				    :requestId, :idempotencyKey, :actorUserId,
				    :originalCollectionJobId, :collectionJobId, :createdAt
				)
				ON CONFLICT (idempotency_key) DO NOTHING
				RETURNING collection_job_id, false AS duplicate, false AS conflict
				""")
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
		return jdbcClient.sql("""
				SELECT collection_job_id,
				       true AS duplicate,
				       original_collection_job_id <> :originalCollectionJobId AS conflict
				FROM admin_recollection_requests
				WHERE idempotency_key = :idempotencyKey
				""")
			.param("idempotencyKey", command.idempotencyKey())
			.param("originalCollectionJobId", command.originalCollectionJobId().value())
			.query(this::mapResult)
			.single();
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

	private AdminRecollectionRequestResult mapResult(ResultSet resultSet, int rowNum) throws SQLException {
		return new AdminRecollectionRequestResult(
			new CollectionJobId(resultSet.getObject("collection_job_id", UUID.class)),
			resultSet.getBoolean("duplicate"),
			resultSet.getBoolean("conflict"));
	}
}
