package com.allermeal.application.port.out;

import com.allermeal.domain.outbox.OutboxEvent;
import java.util.List;

public interface OutboxEventRepository {

	void save(OutboxEvent event);

	List<OutboxEvent> findPending(int limit);

	void markPublished(OutboxEvent event);
}
