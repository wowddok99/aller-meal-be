package com.allermeal.application.port.out.result;

import java.time.Instant;
import java.util.Objects;

public record RawMealResponse(byte[] bytes, Instant receivedAt, long responseTimeMillis) {

	public RawMealResponse {
		bytes = Objects.requireNonNull(bytes, "NEIS 급식 원본은 null일 수 없습니다.").clone();
		Objects.requireNonNull(receivedAt, "NEIS 급식 수신 시각은 null일 수 없습니다.");
		if (responseTimeMillis < 0) {
			throw new IllegalArgumentException("NEIS 응답 시간은 0 이상이어야 합니다.");
		}
	}

	@Override
	public byte[] bytes() {
		return bytes.clone();
	}
}
