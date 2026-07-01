package com.allermeal.infra.retention;

import com.allermeal.application.port.out.OperationLogRetentionRepository;
import com.allermeal.application.retention.OperationLogRetentionDeletionResult;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOperationLogRetentionRepository implements OperationLogRetentionRepository {

	private final JdbcClient jdbcClient;

	public JdbcOperationLogRetentionRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public OperationLogRetentionDeletionResult deleteExpiredNonPersonalLogs(Instant createdBefore, int limitPerTable) {
		Timestamp cutoff = Timestamp.from(createdBefore);
		int adminAuditLogDeletedCount =
			deleteExpiredRows("admin_audit_logs", cutoff, limitPerTable);
		int externalApiLogDeletedCount =
			deleteExpiredRows("external_api_logs", cutoff, limitPerTable);
		int adminDashboardSummarySnapshotDeletedCount =
			deleteExpiredRows("admin_dashboard_summary_snapshots", cutoff, limitPerTable);
		return new OperationLogRetentionDeletionResult(
			adminAuditLogDeletedCount,
			externalApiLogDeletedCount,
			adminDashboardSummarySnapshotDeletedCount);
	}

	private int deleteExpiredRows(String tableName, Timestamp createdBefore, int limit) {
		return jdbcClient.sql("""
				DELETE FROM %s
				WHERE ctid IN (
					SELECT ctid
					FROM %s
					WHERE created_at < :createdBefore
					ORDER BY created_at ASC
					LIMIT :limit
				)
				""".formatted(tableName, tableName))
			.param("createdBefore", createdBefore)
			.param("limit", limit)
			.update();
	}
}
