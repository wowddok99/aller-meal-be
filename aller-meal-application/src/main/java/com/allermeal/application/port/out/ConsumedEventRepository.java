package com.allermeal.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface ConsumedEventRepository {

	boolean record(String consumerName, UUID eventId, String eventType, Instant processedAt);
}
