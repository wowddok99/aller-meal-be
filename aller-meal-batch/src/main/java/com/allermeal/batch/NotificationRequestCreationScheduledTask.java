package com.allermeal.batch;

import com.allermeal.application.notification.NotificationRequestCreationResult;
import com.allermeal.application.notification.NotificationRequestCreationService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "aller-meal.notification-request.scheduler", name = "enabled", havingValue = "true")
public class NotificationRequestCreationScheduledTask {

	private static final Logger log = LoggerFactory.getLogger(NotificationRequestCreationScheduledTask.class);

	private final NotificationRequestCreationService notificationRequestCreationService;
	private final int limit;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public NotificationRequestCreationScheduledTask(
		NotificationRequestCreationService notificationRequestCreationService,
		@Value("${aller-meal.notification-request.scheduler.limit:100}") int limit
	) {
		this.notificationRequestCreationService = notificationRequestCreationService;
		this.limit = limit;
	}

	@Scheduled(fixedDelayString = "${aller-meal.notification-request.scheduler.fixed-delay:PT1M}")
	public void create() {
		if (!running.compareAndSet(false, true)) {
			log.info("알림 요청 생성 Scheduler 실행을 건너뜁니다. reason=already_running");
			return;
		}
		try {
			NotificationRequestCreationResult result = notificationRequestCreationService.createPendingEmailRequests(limit);
			log.info(
				"알림 요청 생성 Scheduler 실행을 완료했습니다. targetCount={}, createdCount={}, duplicateCount={}, correctionCount={}, canceledSupersededCount={}",
				result.targetCount(), result.createdCount(), result.duplicateCount(), result.correctionCount(),
				result.canceledSupersededCount());
		} finally {
			running.set(false);
		}
	}
}
