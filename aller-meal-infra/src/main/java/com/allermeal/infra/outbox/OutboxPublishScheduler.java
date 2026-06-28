package com.allermeal.infra.outbox;

import com.allermeal.application.outbox.OutboxPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aller-meal.outbox.publisher.enabled", havingValue = "true")
public class OutboxPublishScheduler {

	private final OutboxPublisher publisher;
	private final int batchSize;

	public OutboxPublishScheduler(
		OutboxPublisher publisher,
		@Value("${aller-meal.outbox.publisher.batch-size}") int batchSize
	) {
		this.publisher = publisher;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${aller-meal.outbox.publisher.fixed-delay}")
	public void publishPending() {
		publisher.publishPending(batchSize);
	}
}
