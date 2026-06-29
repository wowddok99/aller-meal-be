package com.allermeal.infra.admin;

import com.allermeal.application.port.out.AdminNotificationReprocessRequestRepository;
import com.allermeal.application.port.out.command.AdminNotificationReprocessRequestCommand;
import com.allermeal.application.port.out.result.AdminNotificationReprocessRequestResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminNotificationReprocessRequestRepository implements AdminNotificationReprocessRequestRepository {

	private final JdbcClient jdbcClient;

	public JdbcAdminNotificationReprocessRequestRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public AdminNotificationReprocessRequestResult save(AdminNotificationReprocessRequestCommand command) {
		AdminNotificationReprocessRequestResult result = jdbcClient.sql("""
				INSERT INTO admin_notification_reprocess_requests (
				    reprocess_request_id, idempotency_key, actor_user_id, dead_letter_event_id, created_at
				)
				VALUES (:requestId, :idempotencyKey, :actorUserId, :deadLetterEventId, :createdAt)
				ON CONFLICT (idempotency_key) DO NOTHING
				RETURNING dead_letter_event_id, false AS duplicate, false AS conflict
				""")
			.param("requestId", command.reprocessRequestId())
			.param("idempotencyKey", command.idempotencyKey())
			.param("actorUserId", command.actorUserId().value())
			.param("deadLetterEventId", command.deadLetterEventId())
			.param("createdAt", Timestamp.from(command.createdAt()))
			.query(this::mapResult)
			.optional()
			.orElse(null);
		if (result != null) {
			return result;
		}
		return jdbcClient.sql("""
				SELECT dead_letter_event_id,
				       true AS duplicate,
				       dead_letter_event_id <> :deadLetterEventId AS conflict
				FROM admin_notification_reprocess_requests
				WHERE idempotency_key = :idempotencyKey
				""")
			.param("idempotencyKey", command.idempotencyKey())
			.param("deadLetterEventId", command.deadLetterEventId())
			.query(this::mapResult)
			.single();
	}

	@Override
	public Optional<AdminNotificationReprocessRequestResult> findByIdempotencyKey(
		String idempotencyKey,
		UUID deadLetterEventId
	) {
		return jdbcClient.sql("""
				SELECT dead_letter_event_id,
				       true AS duplicate,
				       dead_letter_event_id <> :deadLetterEventId AS conflict
				FROM admin_notification_reprocess_requests
				WHERE idempotency_key = :idempotencyKey
				""")
			.param("idempotencyKey", idempotencyKey)
			.param("deadLetterEventId", deadLetterEventId)
			.query(this::mapResult)
			.optional();
	}

	private AdminNotificationReprocessRequestResult mapResult(ResultSet resultSet, int rowNum) throws SQLException {
		return new AdminNotificationReprocessRequestResult(
			resultSet.getObject("dead_letter_event_id", UUID.class),
			resultSet.getBoolean("duplicate"),
			resultSet.getBoolean("conflict"));
	}
}
