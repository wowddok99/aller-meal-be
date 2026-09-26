package com.allermeal.infra.outbox;

import com.allermeal.application.port.out.OutboxEventRepository;
import com.allermeal.application.admin.AdminOutboxEventItemResult;
import com.allermeal.application.admin.AdminOutboxEventPageResult;
import com.allermeal.application.admin.AdminOutboxEventQuery;
import com.allermeal.domain.outbox.OutboxEvent;
import com.allermeal.domain.outbox.OutboxEventStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOutboxEventRepository implements OutboxEventRepository {

	private final JdbcTemplate jdbc;
	private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate namedJdbc;

	public JdbcOutboxEventRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.namedJdbc = new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);
	}

	@Override
	public void save(OutboxEvent event) {
		if (event.status() != OutboxEventStatus.PENDING) {
			throw new IllegalArgumentException("신규 Outbox 이벤트는 PENDING 상태여야 합니다.");
		}
		int affectedRows = jdbc.update("""
			insert into outbox_events (event_id, event_type, payload, status, occurred_at, published_at)
			values (?, ?, cast(? as jsonb), ?, ?, ?)
			""",
			event.id(),
			event.type(),
			event.payload(),
			event.status().name(),
			OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC),
			null);
		if (affectedRows != 1) {
			throw new IllegalStateException("Outbox 이벤트 저장에 실패했습니다.");
		}
	}

	@Override
	public List<OutboxEvent> findPending(int limit) {
		if (limit < 1) {
			throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
		}
		return jdbc.query("""
			select event_id, event_type, payload::text, status, occurred_at, published_at
			from outbox_events
			where status = 'PENDING'
			order by occurred_at, event_id
			limit ?
			""", this::mapEvent, limit);
	}

	@Override
	public void markPublished(OutboxEvent event) {
		if (event.status() != OutboxEventStatus.PUBLISHED) {
			throw new IllegalArgumentException("발행 완료 Outbox 이벤트만 상태를 변경할 수 있습니다.");
		}
		int affectedRows = jdbc.update("""
			update outbox_events
			set status = 'PUBLISHED', published_at = ?
			where event_id = ? and status = 'PENDING'
			""",
			OffsetDateTime.ofInstant(event.publishedAt(), ZoneOffset.UTC),
			event.id());
		if (affectedRows != 1) {
			throw new IllegalStateException("Outbox 이벤트 발행 상태 전이에 실패했습니다.");
		}
	}

	@Override
	public AdminOutboxEventPageResult findAdminPage(AdminOutboxEventQuery query) {
		int offset = Math.multiplyExact(query.page() - 1, query.pageSize());
		String predicate = adminPredicate(query);
		Map<String, Object> parameters = adminParameters(query);
		long totalCount = namedJdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE " + predicate, parameters, Long.class);
		var items = namedJdbc.query("""
				SELECT event_id, event_type, status, occurred_at, published_at, created_at, updated_at
				FROM outbox_events WHERE""" + " " + predicate + " " + """
				ORDER BY updated_at DESC, event_id DESC LIMIT :limit OFFSET :offset
				""", withPage(parameters, query.pageSize(), offset), (resultSet, rowNum) -> {
				OffsetDateTime publishedAt = resultSet.getObject("published_at", OffsetDateTime.class);
				return new AdminOutboxEventItemResult(resultSet.getObject("event_id", java.util.UUID.class),
					resultSet.getString("event_type"), OutboxEventStatus.valueOf(resultSet.getString("status")),
					resultSet.getObject("occurred_at", OffsetDateTime.class).toInstant(),
					publishedAt == null ? null : publishedAt.toInstant(),
					resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
					resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());
			});
		return new AdminOutboxEventPageResult(items, query.page(), query.pageSize(), totalCount);
	}

	private String adminPredicate(AdminOutboxEventQuery query) {
		StringBuilder predicate = new StringBuilder("1 = 1");
		if (query.status() != null) predicate.append(" AND status = :status");
		if (query.eventType() != null) predicate.append(" AND event_type = :eventType");
		if (query.query() != null) predicate.append(" AND (CAST(event_id AS text) ILIKE :query ESCAPE '\\' OR event_type ILIKE :query ESCAPE '\\')");
		return predicate.toString();
	}

	private Map<String, Object> adminParameters(AdminOutboxEventQuery query) {
		Map<String, Object> parameters = new java.util.HashMap<>();
		if (query.status() != null) parameters.put("status", query.status().name());
		if (query.eventType() != null) parameters.put("eventType", query.eventType());
		if (query.query() != null) parameters.put("query", "%" + escapeLike(query.query()) + "%");
		return parameters;
	}

	private Map<String, Object> withPage(Map<String, Object> parameters, int limit, int offset) {
		Map<String, Object> paged = new java.util.HashMap<>(parameters);
		paged.put("limit", limit);
		paged.put("offset", offset);
		return paged;
	}

	private String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private OutboxEvent mapEvent(ResultSet result, int rowNumber) throws SQLException {
		OffsetDateTime publishedAt = result.getObject("published_at", OffsetDateTime.class);
		return new OutboxEvent(
			result.getObject("event_id", java.util.UUID.class),
			result.getString("event_type"),
			result.getString("payload"),
			OutboxEventStatus.valueOf(result.getString("status")),
			result.getObject("occurred_at", OffsetDateTime.class).toInstant(),
			publishedAt == null ? null : publishedAt.toInstant());
	}
}
