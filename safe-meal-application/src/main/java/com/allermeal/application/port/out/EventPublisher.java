package com.allermeal.application.port.out;

import com.allermeal.domain.outbox.OutboxEvent;

@FunctionalInterface
public interface EventPublisher {

	void publish(OutboxEvent event);
}
