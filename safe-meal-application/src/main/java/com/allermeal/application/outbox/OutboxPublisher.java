package com.allermeal.application.outbox;

import com.allermeal.application.port.out.EventPublisher;
import com.allermeal.application.port.out.OutboxEventRepository;
import java.time.Clock;

public class OutboxPublisher {

	private final OutboxEventRepository repository;
	private final EventPublisher eventPublisher;
	private final Clock clock;

	public OutboxPublisher(OutboxEventRepository repository, EventPublisher eventPublisher, Clock clock) {
		this.repository = repository;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	public int publishPending(int limit) {
		int publishedCount = 0;
		for (var event : repository.findPending(limit)) {
			eventPublisher.publish(event);
			repository.markPublished(event.markPublished(clock.instant()));
			publishedCount++;
		}
		return publishedCount;
	}
}
