package com.allermeal.batch;

import com.allermeal.application.retention.DataRetentionCleanupResult;
import com.allermeal.application.retention.DataRetentionCleanupService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "aller-meal.retention.cleanup.scheduler", name = "enabled", havingValue = "true")
public class DataRetentionCleanupScheduledTask {

	private static final Logger log = LoggerFactory.getLogger(DataRetentionCleanupScheduledTask.class);

	private final DataRetentionCleanupService cleanupService;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public DataRetentionCleanupScheduledTask(DataRetentionCleanupService cleanupService) {
		this.cleanupService = cleanupService;
	}

	@Scheduled(fixedDelayString = "${aller-meal.retention.cleanup.scheduler.fixed-delay:PT24H}")
	public void cleanup() {
		if (!running.compareAndSet(false, true)) {
			log.info("데이터 보존 Scheduler 실행을 건너뜁니다. reason=already_running");
			return;
		}
		try {
			DataRetentionCleanupResult result = cleanupService.cleanupExpiredData();
			log.info(
				"데이터 보존 Scheduler 실행을 완료했습니다. rawPayloadExpiresBeforeInclusive={}, rawPayloadScannedCount={}, rawPayloadDeletedCount={}, rawPayloadFailedCount={}, operationLogCreatedBefore={}, adminAuditLogDeletedCount={}, externalApiLogDeletedCount={}, adminDashboardSummarySnapshotDeletedCount={}",
				result.rawPayloadExpiresBeforeInclusive(),
				result.rawPayloadScannedCount(),
				result.rawPayloadDeletedCount(),
				result.rawPayloadFailedCount(),
				result.operationLogCreatedBefore(),
				result.operationLogDeletion().adminAuditLogDeletedCount(),
				result.operationLogDeletion().externalApiLogDeletedCount(),
				result.operationLogDeletion().adminDashboardSummarySnapshotDeletedCount());
		} finally {
			running.set(false);
		}
	}
}
