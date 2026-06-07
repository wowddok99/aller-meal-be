package com.allermeal.infra.consumer;

import com.allermeal.application.port.out.ConsumedEventRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConsumedEventRepository implements ConsumedEventRepository {

	private final JdbcTemplate jdbc;

	public JdbcConsumedEventRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean record(String consumerName, UUID eventId, String eventType, Instant processedAt) {
		return jdbc.update("""
			insert into consumed_events (consumer_name, event_id, event_type, processed_at)
			values (?, ?, ?, ?)
			on conflict (consumer_name, event_id) do nothing
			""",
			consumerName,
			eventId,
			eventType,
			OffsetDateTime.ofInstant(processedAt, ZoneOffset.UTC)) == 1;
	}
}
