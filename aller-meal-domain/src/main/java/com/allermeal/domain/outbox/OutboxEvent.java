package com.allermeal.domain.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OutboxEvent(
	UUID id,
	String type,
	String payload,
	OutboxEventStatus status,
	Instant occurredAt,
	Instant publishedAt
) {

	public OutboxEvent {
		Objects.requireNonNull(id, "id는 null일 수 없습니다.");
		type = requireText(type, "type");
		payload = requireText(payload, "payload");
		Objects.requireNonNull(status, "status는 null일 수 없습니다.");
		Objects.requireNonNull(occurredAt, "occurredAt은 null일 수 없습니다.");
		if (status == OutboxEventStatus.PENDING && publishedAt != null) {
			throw new IllegalArgumentException("PENDING 이벤트는 publishedAt을 가질 수 없습니다.");
		}
		if (status == OutboxEventStatus.PUBLISHED && publishedAt == null) {
			throw new IllegalArgumentException("PUBLISHED 이벤트에는 publishedAt이 필요합니다.");
		}
		if (publishedAt != null && publishedAt.isBefore(occurredAt)) {
			throw new IllegalArgumentException("publishedAt은 occurredAt보다 이전일 수 없습니다.");
		}
	}

	public static OutboxEvent pending(UUID id, String type, String payload, Instant occurredAt) {
		return new OutboxEvent(id, type, payload, OutboxEventStatus.PENDING, occurredAt, null);
	}

	public OutboxEvent markPublished(Instant publishedAt) {
		if (status != OutboxEventStatus.PENDING) {
			throw new IllegalStateException("PENDING 이벤트만 발행할 수 있습니다.");
		}
		return new OutboxEvent(id, type, payload, OutboxEventStatus.PUBLISHED, occurredAt, publishedAt);
	}

	private static String requireText(String value, String name) {
		Objects.requireNonNull(value, name + " 값은 null일 수 없습니다.");
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " 값은 비어 있을 수 없습니다.");
		}
		return value;
	}
}
