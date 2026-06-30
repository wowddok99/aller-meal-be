package com.allermeal.infra.admin;

import com.allermeal.application.admin.AdminDashboardCollectionSummaryResult;
import com.allermeal.application.admin.AdminDashboardDeadLetterSummaryResult;
import com.allermeal.application.admin.AdminDashboardLabelingSummaryResult;
import com.allermeal.application.admin.AdminDashboardNotificationSummaryResult;
import com.allermeal.application.admin.AdminDashboardOutboxSummaryResult;
import com.allermeal.application.admin.AdminDashboardSummaryResult;
import com.allermeal.application.port.out.AdminDashboardSummaryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAdminDashboardSummaryRepository implements AdminDashboardSummaryRepository {

	private static final long REFRESH_LOCK_KEY = 7_404_621_064L;

	private final JdbcClient jdbcClient;

	public JdbcAdminDashboardSummaryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	@Transactional
	public AdminDashboardSummaryResult findOrCreate(Instant generatedAfter, Instant now) {
		AdminDashboardSummaryResult fresh = findLatest(generatedAfter);
		if (fresh != null) {
			return fresh;
		}
		AdminDashboardSummaryResult latest = findLatest(null);
		boolean refreshLockAcquired = tryRefreshLock();
		if (!refreshLockAcquired && latest != null) {
			return latest;
		}
		if (!refreshLockAcquired) {
			waitRefreshLock();
		}
		fresh = findLatest(generatedAfter);
		if (fresh != null) {
			return fresh;
		}
		AdminDashboardSummaryResult snapshot = aggregate(now);
		save(snapshot);
		return snapshot;
	}

	private AdminDashboardSummaryResult aggregate(Instant generatedAt) {
		return jdbcClient.sql("""
				SELECT
				    (SELECT count(*) FROM collection_jobs WHERE status = 'PENDING') AS collection_pending_count,
				    (SELECT count(*) FROM collection_jobs WHERE status = 'RUNNING') AS collection_running_count,
				    (SELECT count(*) FROM collection_jobs WHERE status = 'SUCCEEDED') AS collection_succeeded_count,
				    (SELECT count(*) FROM collection_jobs WHERE status = 'FAILED') AS collection_failed_count,
				    (SELECT count(*) FROM meal_items WHERE labeling_status = 'PENDING') AS labeling_pending_count,
				    (SELECT count(*) FROM meal_items WHERE labeling_status = 'LABELED') AS labeling_labeled_count,
				    (SELECT count(*) FROM meal_items WHERE labeling_status = 'UNKNOWN') AS labeling_unknown_count,
				    (SELECT count(*) FROM meal_items WHERE labeling_status = 'LABELING_FAILED') AS labeling_failed_count,
				    (SELECT count(*) FROM outbox_events WHERE status = 'PENDING') AS outbox_pending_count,
				    (SELECT count(*) FROM outbox_events WHERE status = 'PUBLISHED') AS outbox_published_count,
				    (SELECT count(*) FROM dead_letter_events WHERE status = 'PENDING') AS dlq_pending_count,
				    (SELECT count(*) FROM dead_letter_events WHERE status = 'REPROCESSED') AS dlq_reprocessed_count,
				    (SELECT count(*) FROM notification_requests WHERE status = 'PENDING') AS notification_pending_count,
				    (SELECT count(*) FROM notification_requests WHERE status = 'SENDING') AS notification_sending_count,
				    (SELECT count(*) FROM notification_requests WHERE status = 'RETRY_PENDING') AS notification_retry_pending_count,
				    (SELECT count(*) FROM notification_requests WHERE status = 'SENT') AS notification_sent_count,
				    (SELECT count(*) FROM notification_requests WHERE status = 'FAILED') AS notification_failed_count,
				    (SELECT count(*) FROM notification_requests WHERE status = 'CANCELED') AS notification_canceled_count
				""")
			.query((resultSet, rowNumber) -> new AdminDashboardSummaryResult(
				generatedAt,
				new AdminDashboardCollectionSummaryResult(
					resultSet.getLong("collection_pending_count"),
					resultSet.getLong("collection_running_count"),
					resultSet.getLong("collection_succeeded_count"),
					resultSet.getLong("collection_failed_count")),
				new AdminDashboardLabelingSummaryResult(
					resultSet.getLong("labeling_pending_count"),
					resultSet.getLong("labeling_labeled_count"),
					resultSet.getLong("labeling_unknown_count"),
					resultSet.getLong("labeling_failed_count")),
				new AdminDashboardOutboxSummaryResult(
					resultSet.getLong("outbox_pending_count"),
					resultSet.getLong("outbox_published_count")),
				new AdminDashboardDeadLetterSummaryResult(
					resultSet.getLong("dlq_pending_count"),
					resultSet.getLong("dlq_reprocessed_count")),
				new AdminDashboardNotificationSummaryResult(
					resultSet.getLong("notification_pending_count"),
					resultSet.getLong("notification_sending_count"),
					resultSet.getLong("notification_retry_pending_count"),
					resultSet.getLong("notification_sent_count"),
					resultSet.getLong("notification_failed_count"),
					resultSet.getLong("notification_canceled_count"))))
			.single();
	}

	private boolean tryRefreshLock() {
		return Boolean.TRUE.equals(jdbcClient.sql("SELECT pg_try_advisory_xact_lock(:lockKey)")
			.param("lockKey", REFRESH_LOCK_KEY)
			.query(Boolean.class)
			.single());
	}

	private void waitRefreshLock() {
		jdbcClient.sql("SELECT pg_advisory_xact_lock(:lockKey)")
			.param("lockKey", REFRESH_LOCK_KEY)
			.query((resultSet, rowNumber) -> true)
			.single();
	}

	private AdminDashboardSummaryResult findLatest(Instant generatedAfter) {
		String predicate = generatedAfter == null ? "" : "WHERE generated_at >= :generatedAfter ";
		var statement = jdbcClient.sql("""
				SELECT generated_at,
				       collection_pending_count, collection_running_count,
				       collection_succeeded_count, collection_failed_count,
				       labeling_pending_count, labeling_labeled_count,
				       labeling_unknown_count, labeling_failed_count,
				       outbox_pending_count, outbox_published_count,
				       dlq_pending_count, dlq_reprocessed_count,
				       notification_pending_count, notification_sending_count,
				       notification_retry_pending_count, notification_sent_count,
				       notification_failed_count, notification_canceled_count
				FROM admin_dashboard_summary_snapshots
				""" + predicate + """
				ORDER BY generated_at DESC
				LIMIT 1
				""");
		if (generatedAfter != null) {
			statement = statement.param("generatedAfter", OffsetDateTime.ofInstant(generatedAfter, ZoneOffset.UTC));
		}
		return statement.query(this::mapSummary).optional().orElse(null);
	}

	private void save(AdminDashboardSummaryResult summary) {
		jdbcClient.sql("""
				INSERT INTO admin_dashboard_summary_snapshots (
				    summary_snapshot_id, generated_at,
				    collection_pending_count, collection_running_count,
				    collection_succeeded_count, collection_failed_count,
				    labeling_pending_count, labeling_labeled_count,
				    labeling_unknown_count, labeling_failed_count,
				    outbox_pending_count, outbox_published_count,
				    dlq_pending_count, dlq_reprocessed_count,
				    notification_pending_count, notification_sending_count,
				    notification_retry_pending_count, notification_sent_count,
				    notification_failed_count, notification_canceled_count
				)
				VALUES (
				    :snapshotId, :generatedAt,
				    :collectionPendingCount, :collectionRunningCount,
				    :collectionSucceededCount, :collectionFailedCount,
				    :labelingPendingCount, :labelingLabeledCount,
				    :labelingUnknownCount, :labelingFailedCount,
				    :outboxPendingCount, :outboxPublishedCount,
				    :dlqPendingCount, :dlqReprocessedCount,
				    :notificationPendingCount, :notificationSendingCount,
				    :notificationRetryPendingCount, :notificationSentCount,
				    :notificationFailedCount, :notificationCanceledCount
				)
				""")
			.param("snapshotId", UUID.randomUUID())
			.param("generatedAt", OffsetDateTime.ofInstant(summary.generatedAt(), ZoneOffset.UTC))
			.param("collectionPendingCount", summary.collection().pendingCount())
			.param("collectionRunningCount", summary.collection().runningCount())
			.param("collectionSucceededCount", summary.collection().succeededCount())
			.param("collectionFailedCount", summary.collection().failedCount())
			.param("labelingPendingCount", summary.labeling().pendingCount())
			.param("labelingLabeledCount", summary.labeling().labeledCount())
			.param("labelingUnknownCount", summary.labeling().unknownCount())
			.param("labelingFailedCount", summary.labeling().labelingFailedCount())
			.param("outboxPendingCount", summary.outbox().pendingCount())
			.param("outboxPublishedCount", summary.outbox().publishedCount())
			.param("dlqPendingCount", summary.dlq().pendingCount())
			.param("dlqReprocessedCount", summary.dlq().reprocessedCount())
			.param("notificationPendingCount", summary.notifications().pendingCount())
			.param("notificationSendingCount", summary.notifications().sendingCount())
			.param("notificationRetryPendingCount", summary.notifications().retryPendingCount())
			.param("notificationSentCount", summary.notifications().sentCount())
			.param("notificationFailedCount", summary.notifications().failedCount())
			.param("notificationCanceledCount", summary.notifications().canceledCount())
			.update();
	}

	private AdminDashboardSummaryResult mapSummary(ResultSet resultSet, int rowNumber) throws SQLException {
		return new AdminDashboardSummaryResult(
			resultSet.getObject("generated_at", OffsetDateTime.class).toInstant(),
			new AdminDashboardCollectionSummaryResult(
				resultSet.getLong("collection_pending_count"),
				resultSet.getLong("collection_running_count"),
				resultSet.getLong("collection_succeeded_count"),
				resultSet.getLong("collection_failed_count")),
			new AdminDashboardLabelingSummaryResult(
				resultSet.getLong("labeling_pending_count"),
				resultSet.getLong("labeling_labeled_count"),
				resultSet.getLong("labeling_unknown_count"),
				resultSet.getLong("labeling_failed_count")),
			new AdminDashboardOutboxSummaryResult(
				resultSet.getLong("outbox_pending_count"),
				resultSet.getLong("outbox_published_count")),
			new AdminDashboardDeadLetterSummaryResult(
				resultSet.getLong("dlq_pending_count"),
				resultSet.getLong("dlq_reprocessed_count")),
			new AdminDashboardNotificationSummaryResult(
				resultSet.getLong("notification_pending_count"),
				resultSet.getLong("notification_sending_count"),
				resultSet.getLong("notification_retry_pending_count"),
				resultSet.getLong("notification_sent_count"),
				resultSet.getLong("notification_failed_count"),
				resultSet.getLong("notification_canceled_count")));
	}

}
