package com.allermeal.batch;

import com.allermeal.application.meal.RegisteredSchoolMealCollectionScheduler;
import com.allermeal.application.meal.ScheduledMealCollectionResult;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "aller-meal.collection.scheduler", name = "enabled", havingValue = "true")
public class RegisteredSchoolMealCollectionScheduledTask {

	private static final Logger log = LoggerFactory.getLogger(RegisteredSchoolMealCollectionScheduledTask.class);

	private final RegisteredSchoolMealCollectionScheduler scheduler;
	private final int daysAhead;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public RegisteredSchoolMealCollectionScheduledTask(
		RegisteredSchoolMealCollectionScheduler scheduler,
		@Value("${aller-meal.collection.scheduler.days-ahead:7}") int daysAhead
	) {
		this.scheduler = scheduler;
		this.daysAhead = daysAhead;
	}

	@Scheduled(fixedDelayString = "${aller-meal.collection.scheduler.fixed-delay:PT1H}")
	public void collect() {
		if (!running.compareAndSet(false, true)) {
			log.info("등록 학교 급식 수집 Scheduler 실행을 건너뜁니다. reason=already_running");
			return;
		}
		try {
			ScheduledMealCollectionResult result = scheduler.collect(daysAhead);
			log.info(
				"등록 학교 급식 수집 Scheduler 실행을 완료했습니다. activeSubscriptionCount={}, targetCount={}, requestedJobCount={}, skippedJobCount={}",
				result.activeSubscriptionCount(), result.targetCount(), result.requestedJobCount(),
				result.skippedJobCount());
		} finally {
			running.set(false);
		}
	}
}
