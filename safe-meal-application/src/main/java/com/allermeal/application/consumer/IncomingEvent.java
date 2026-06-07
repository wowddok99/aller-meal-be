package com.allermeal.application.consumer;

import java.util.Objects;
import java.util.UUID;

public record IncomingEvent(UUID id, String type, String payload) {

	public IncomingEvent {
		Objects.requireNonNull(id, "id는 null일 수 없습니다.");
		type = requireText(type, "type");
		payload = requireText(payload, "payload");
	}

	private static String requireText(String value, String name) {
		Objects.requireNonNull(value, name + " 값은 null일 수 없습니다.");
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " 값은 비어 있을 수 없습니다.");
		}
		return value;
	}
}
