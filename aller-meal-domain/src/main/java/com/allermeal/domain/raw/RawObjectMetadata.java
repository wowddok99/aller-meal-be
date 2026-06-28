package com.allermeal.domain.raw;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RawObjectMetadata(
	UUID id,
	String objectKey,
	String sha256Hash,
	long sizeBytes,
	Instant receivedAt,
	Instant expiresAt
) {

	public RawObjectMetadata {
		Objects.requireNonNull(id, "원본 객체 메타데이터 ID는 null일 수 없습니다.");
		objectKey = requireText(objectKey, "원본 객체 키는 필수입니다.");
		sha256Hash = requireText(sha256Hash, "원본 객체 해시는 필수입니다.");
		Objects.requireNonNull(receivedAt, "원본 수신 시각은 null일 수 없습니다.");
		Objects.requireNonNull(expiresAt, "원본 만료 시각은 null일 수 없습니다.");
		if (sizeBytes < 0) {
			throw new IllegalArgumentException("원본 객체 크기는 음수일 수 없습니다.");
		}
		if (!expiresAt.isAfter(receivedAt)) {
			throw new IllegalArgumentException("원본 만료 시각은 수신 시각 이후여야 합니다.");
		}
	}

	private static String requireText(String value, String message) {
		Objects.requireNonNull(value, message);
		if (value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}
}
