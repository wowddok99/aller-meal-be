package com.allermeal.application.consumer;

import com.allermeal.application.port.out.ConsumedEventRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Consumer;

public class IdempotentEventConsumer {

	private final ConsumedEventRepository consumedEvents;
	private final Clock clock;

	public IdempotentEventConsumer(ConsumedEventRepository consumedEvents, Clock clock) {
		this.consumedEvents = consumedEvents;
		this.clock = clock;
	}

	public boolean consume(String consumerName, IncomingEvent event, Consumer<IncomingEvent> handler) {
		Objects.requireNonNull(handler, "handler는 null일 수 없습니다.");
		if (!consumedEvents.record(consumerName, event.id(), event.type(), clock.instant())) {
			return false;
		}
		handler.accept(event);
		return true;
	}
}
