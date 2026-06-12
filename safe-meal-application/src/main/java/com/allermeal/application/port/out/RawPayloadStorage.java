package com.allermeal.application.port.out;

import com.allermeal.domain.raw.RawObjectMetadata;
import java.time.Instant;
import java.util.Objects;

public interface RawPayloadStorage {

	RawObjectMetadata store(RawPayload payload);

	record RawPayload(String source, byte[] bytes, String contentType, Instant receivedAt, Instant expiresAt) {

		public RawPayload {
			if (source == null || !source.matches("[a-z0-9-]+")) {
				throw new IllegalArgumentException("원본 출처는 영문 소문자, 숫자, 하이픈만 사용할 수 있습니다.");
			}
			bytes = Objects.requireNonNull(bytes, "원본 payload는 null일 수 없습니다.").clone();
			if (contentType == null || contentType.isBlank()) {
				throw new IllegalArgumentException("원본 content type은 필수입니다.");
			}
			Objects.requireNonNull(receivedAt, "원본 수신 시각은 null일 수 없습니다.");
			Objects.requireNonNull(expiresAt, "원본 만료 시각은 null일 수 없습니다.");
			if (!expiresAt.isAfter(receivedAt)) {
				throw new IllegalArgumentException("원본 만료 시각은 수신 시각 이후여야 합니다.");
			}
		}

		@Override
		public byte[] bytes() {
			return bytes.clone();
		}
	}
}
