package com.allermeal.batch;

import com.allermeal.application.account.ExpiredAccountPersonalDataCleanupResult;
import com.allermeal.application.account.ExpiredAccountPersonalDataCleanupService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "aller-meal.account-withdrawal.cleanup.scheduler", name = "enabled", havingValue = "true")
public class ExpiredAccountPersonalDataCleanupScheduledTask {

	private static final Logger log = LoggerFactory.getLogger(ExpiredAccountPersonalDataCleanupScheduledTask.class);

	private final ExpiredAccountPersonalDataCleanupService cleanupService;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public ExpiredAccountPersonalDataCleanupScheduledTask(
		ExpiredAccountPersonalDataCleanupService cleanupService
	) {
		this.cleanupService = cleanupService;
	}

	@Scheduled(fixedDelayString = "${aller-meal.account-withdrawal.cleanup.scheduler.fixed-delay:PT1H}")
	public void cleanup() {
		if (!running.compareAndSet(false, true)) {
			log.info("탈퇴 개인정보 삭제 Scheduler 실행을 건너뜁니다. reason=already_running");
			return;
		}
		try {
			ExpiredAccountPersonalDataCleanupResult result = cleanupService.deleteExpiredPersonalData();
			log.info("탈퇴 개인정보 삭제 Scheduler 실행을 완료했습니다. dueBeforeInclusive={}, deletedUserCount={}",
				result.dueBeforeInclusive(), result.deletedUserCount());
		} finally {
			running.set(false);
		}
	}
}
