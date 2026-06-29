package com.allermeal.infra.consumer;

import com.allermeal.application.admin.AdminDeadLetterEventItemResult;
import com.allermeal.application.admin.AdminDeadLetterEventPageResult;
import com.allermeal.application.admin.AdminDeadLetterEventStatus;
import com.allermeal.application.port.out.DeadLetterEventRepository;
import com.allermeal.application.port.out.command.DeadLetterEventCommand;
import com.allermeal.domain.user.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDeadLetterEventRepository implements DeadLetterEventRepository {

	private final JdbcClient jdbcClient;

	public JdbcDeadLetterEventRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public void save(DeadLetterEventCommand command) {
		jdbcClient.sql("""
				INSERT INTO dead_letter_events (
				    dead_letter_event_id, message_id, event_type, payload, retry_count,
				    status, created_at, updated_at
				)
				VALUES (
				    :deadLetterEventId, :messageId, :eventType, :payload, :retryCount,
				    'PENDING', :createdAt, :createdAt
				)
				ON CONFLICT (message_id, event_type) DO NOTHING
				""")
			.param("deadLetterEventId", command.deadLetterEventId())
			.param("messageId", command.messageId())
			.param("eventType", command.eventType())
			.param("payload", command.payload())
			.param("retryCount", command.retryCount())
			.param("createdAt", Timestamp.from(command.createdAt()))
			.update();
	}

	@Override
	public AdminDeadLetterEventPageResult findRecent(int page, int pageSize) {
		int offset = offset(page, pageSize);
		long totalCount = jdbcClient.sql("SELECT count(*) FROM dead_letter_events")
			.query(Long.class)
			.single();
		List<AdminDeadLetterEventItemResult> items = jdbcClient.sql("""
				SELECT dead_letter_event_id, message_id, event_type, payload, retry_count,
				       status, reprocessed_by_user_id, reprocessed_at, created_at, updated_at
				FROM dead_letter_events
				ORDER BY created_at DESC, dead_letter_event_id DESC
				LIMIT :limit OFFSET :offset
				""")
			.param("limit", pageSize)
			.param("offset", offset)
			.query(this::mapItem)
			.list();
		return new AdminDeadLetterEventPageResult(items, page, pageSize, totalCount);
	}

	@Override
	public Optional<AdminDeadLetterEventItemResult> findById(UUID deadLetterEventId) {
		return jdbcClient.sql("""
				SELECT dead_letter_event_id, message_id, event_type, payload, retry_count,
				       status, reprocessed_by_user_id, reprocessed_at, created_at, updated_at
				FROM dead_letter_events
				WHERE dead_letter_event_id = :deadLetterEventId
				""")
			.param("deadLetterEventId", deadLetterEventId)
			.query(this::mapItem)
			.optional();
	}

	@Override
	public boolean markReprocessed(UUID deadLetterEventId, UserId actorUserId, java.time.Instant reprocessedAt) {
		int rows = jdbcClient.sql("""
				UPDATE dead_letter_events
				SET status = 'REPROCESSED',
				    reprocessed_by_user_id = :actorUserId,
				    reprocessed_at = :reprocessedAt,
				    updated_at = :reprocessedAt
				WHERE dead_letter_event_id = :deadLetterEventId
				  AND status = 'PENDING'
				""")
			.param("actorUserId", actorUserId.value())
			.param("reprocessedAt", Timestamp.from(reprocessedAt))
			.param("deadLetterEventId", deadLetterEventId)
			.update();
		return rows == 1;
	}

	private int offset(int page, int pageSize) {
		try {
			return Math.multiplyExact(page - 1, pageSize);
		} catch (ArithmeticException exception) {
			throw new IllegalArgumentException("DLQ 이벤트 페이지 offset이 허용 범위를 초과했습니다.", exception);
		}
	}

	private AdminDeadLetterEventItemResult mapItem(ResultSet resultSet, int rowNum) throws SQLException {
		UUID reprocessedBy = resultSet.getObject("reprocessed_by_user_id", UUID.class);
		OffsetDateTime reprocessedAt = resultSet.getObject("reprocessed_at", OffsetDateTime.class);
		return new AdminDeadLetterEventItemResult(
			resultSet.getObject("dead_letter_event_id", UUID.class),
			resultSet.getString("message_id"),
			resultSet.getString("event_type"),
			resultSet.getString("payload"),
			resultSet.getInt("retry_count"),
			AdminDeadLetterEventStatus.valueOf(resultSet.getString("status")),
			reprocessedBy == null ? null : new UserId(reprocessedBy),
			reprocessedAt == null ? null : reprocessedAt.toInstant(),
			resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
			resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());
	}
}
