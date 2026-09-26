package com.allermeal.application.admin;

import com.allermeal.domain.outbox.OutboxEventStatus;

public record AdminOutboxEventQuery(int page, int pageSize, OutboxEventStatus status, String eventType, String query) {
}
