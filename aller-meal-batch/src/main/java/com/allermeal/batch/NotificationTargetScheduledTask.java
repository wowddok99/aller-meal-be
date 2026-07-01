package com.allermeal.batch;

import com.allermeal.application.notification.NotificationTargetScheduler;
import com.allermeal.application.notification.ScheduledNotificationTargetGenerationResult;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "aller-meal.notification-target.scheduler", name = "enabled", havingValue = "true")
public class NotificationTargetScheduledTask {

	private static final Logger log = LoggerFactory.getLogger(NotificationTargetScheduledTask.class);

	private final NotificationTargetScheduler scheduler;
	private final Duration lookback;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public NotificationTargetScheduledTask(
		NotificationTargetScheduler scheduler,
		@Value("${aller-meal.notification-target.scheduler.lookback:PT5M}") Duration lookback
	) {
		this.scheduler = scheduler;
		this.lookback = lookback;
	}

	@Scheduled(fixedDelayString = "${aller-meal.notification-target.scheduler.fixed-delay:PT1M}")
	public void generate() {
		if (!running.compareAndSet(false, true)) {
			log.info("알림 대상 생성 Scheduler 실행을 건너뜁니다. reason=already_running");
			return;
		}
		try {
			ScheduledNotificationTargetGenerationResult result = scheduler.generateDue(lookback);
			log.info(
				"알림 대상 생성 Scheduler 실행을 완료했습니다. duePreferenceCount={}, createdTargetCount={}, duplicateTargetCount={}",
				result.duePreferenceCount(), result.createdTargetCount(), result.duplicateTargetCount());
		} finally {
			running.set(false);
		}
	}
}
