package com.allermeal.application.port.out;

import com.allermeal.domain.outbox.OutboxEvent;
import com.allermeal.application.admin.AdminOutboxEventPageResult;
import com.allermeal.application.admin.AdminOutboxEventQuery;
import java.util.List;

public interface OutboxEventRepository {

	void save(OutboxEvent event);

	List<OutboxEvent> findPending(int limit);

	void markPublished(OutboxEvent event);

	AdminOutboxEventPageResult findAdminPage(AdminOutboxEventQuery query);
}
