package com.allermeal.domain.common;

import java.time.Instant;
import java.util.Objects;

public record EntityTimestamps(Instant createdAt, Instant updatedAt) {

	public EntityTimestamps {
		Objects.requireNonNull(createdAt, "createdAt은 null일 수 없습니다.");
		Objects.requireNonNull(updatedAt, "updatedAt은 null일 수 없습니다.");
		if (updatedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("updatedAt은 createdAt보다 이전일 수 없습니다.");
		}
	}

	public static EntityTimestamps createdAt(Instant createdAt) {
		return new EntityTimestamps(createdAt, createdAt);
	}
}
