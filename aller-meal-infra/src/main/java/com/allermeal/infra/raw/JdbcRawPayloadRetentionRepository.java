package com.allermeal.infra.raw;

import com.allermeal.application.port.out.RawPayloadRetentionRepository;
import com.allermeal.application.port.out.result.ExpiredRawPayloadResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRawPayloadRetentionRepository implements RawPayloadRetentionRepository {

	private final JdbcClient jdbcClient;

	public JdbcRawPayloadRetentionRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public List<ExpiredRawPayloadResult> findExpiredRawPayloads(Instant expiresBeforeInclusive, int limit) {
		return jdbcClient.sql("""
				SELECT raw_object_id, object_key, expires_at
				FROM raw_meal_objects
				WHERE expires_at <= :expiresBeforeInclusive
				ORDER BY expires_at ASC, raw_object_id ASC
				LIMIT :limit
				""")
			.param("expiresBeforeInclusive", Timestamp.from(expiresBeforeInclusive))
			.param("limit", limit)
			.query(this::mapExpiredRawPayload)
			.list();
	}

	@Override
	public boolean deleteMetadata(UUID rawObjectId, Instant selectedExpiresAt) {
		int rows = jdbcClient.sql("""
				DELETE FROM raw_meal_objects
				WHERE raw_object_id = :rawObjectId
				  AND expires_at = :selectedExpiresAt
				""")
			.param("rawObjectId", rawObjectId)
			.param("selectedExpiresAt", Timestamp.from(selectedExpiresAt))
			.update();
		return rows == 1;
	}

	private ExpiredRawPayloadResult mapExpiredRawPayload(ResultSet resultSet, int rowNum) throws SQLException {
		return new ExpiredRawPayloadResult(
			resultSet.getObject("raw_object_id", UUID.class),
			resultSet.getString("object_key"),
			resultSet.getObject("expires_at", OffsetDateTime.class).toInstant());
	}
}
