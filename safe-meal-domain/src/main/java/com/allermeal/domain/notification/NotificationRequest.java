package com.allermeal.domain.notification;

import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.common.EntityTimestamps;
import com.allermeal.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record NotificationRequest(
	NotificationId id,
	UUID notificationTargetId,
	ChildProfileId childProfileId,
	UserId ownerId,
	LocalDate notificationDate,
	NotificationChannel channel,
	NotificationReason reason,
	String dedupKey,
	String correctionKey,
	String contentVersion,
	boolean correction,
	NotificationId supersedesNotificationId,
	NotificationStatus status,
	int attemptCount,
	int maxAttempts,
	Instant nextAttemptAt,
	Instant sentAt,
	String failureCode,
	String failureMessage,
	EntityTimestamps timestamps
) {

	private static final int MAX_FAILURE_CODE_LENGTH = 100;
	private static final int MAX_FAILURE_MESSAGE_LENGTH = 1000;

	public NotificationRequest {
		Objects.requireNonNull(id, "알림 ID는 null일 수 없습니다.");
		Objects.requireNonNull(notificationTargetId, "알림 대상 ID는 null일 수 없습니다.");
		Objects.requireNonNull(childProfileId, "자녀 ID는 null일 수 없습니다.");
		Objects.requireNonNull(ownerId, "소유자 ID는 null일 수 없습니다.");
		Objects.requireNonNull(notificationDate, "알림 날짜는 null일 수 없습니다.");
		Objects.requireNonNull(channel, "알림 채널은 null일 수 없습니다.");
		Objects.requireNonNull(reason, "알림 사유는 null일 수 없습니다.");
		dedupKey = requireSha256Hex(dedupKey, "알림 중복 key");
		correctionKey = requireSha256Hex(correctionKey, "알림 정정 key");
		contentVersion = requireSha256Hex(contentVersion, "알림 내용 version");
		Objects.requireNonNull(status, "알림 상태는 null일 수 없습니다.");
		if (attemptCount < 0) {
			throw new IllegalArgumentException("알림 발송 시도 횟수는 음수일 수 없습니다.");
		}
		if (maxAttempts < 1 || maxAttempts > 10) {
			throw new IllegalArgumentException("알림 최대 발송 시도 횟수는 1 이상 10 이하여야 합니다.");
		}
		if (attemptCount > maxAttempts) {
			throw new IllegalArgumentException("알림 발송 시도 횟수는 최대 횟수를 초과할 수 없습니다.");
		}
		failureCode = normalizeNullable(failureCode, MAX_FAILURE_CODE_LENGTH, "알림 실패 code");
		failureMessage = normalizeNullable(failureMessage, MAX_FAILURE_MESSAGE_LENGTH, "알림 실패 메시지");
		Objects.requireNonNull(timestamps, "알림 시각 정보는 null일 수 없습니다.");
		validateStatusFields(status, attemptCount, nextAttemptAt, sentAt, failureCode, failureMessage);
		if (correction != (supersedesNotificationId != null)) {
			throw new IllegalArgumentException("정정 알림은 대체 대상 알림 ID가 필요합니다.");
		}
	}

	public static NotificationRequest pending(
		NotificationId id,
		UUID notificationTargetId,
		ChildProfileId childProfileId,
		UserId ownerId,
		LocalDate notificationDate,
		NotificationChannel channel,
		NotificationReason reason,
		String dedupKey,
		String correctionKey,
		String contentVersion,
		boolean correction,
		NotificationId supersedesNotificationId,
		int maxAttempts,
		Instant createdAt
	) {
		return new NotificationRequest(id, notificationTargetId, childProfileId, ownerId, notificationDate, channel,
			reason, dedupKey, correctionKey, contentVersion, correction, supersedesNotificationId,
			NotificationStatus.PENDING, 0, maxAttempts, createdAt, null, null, null,
			EntityTimestamps.createdAt(createdAt));
	}

	public boolean isActive() {
		return status == NotificationStatus.PENDING
			|| status == NotificationStatus.SENDING
			|| status == NotificationStatus.RETRY_PENDING;
	}

	public NotificationRequest startSending(Instant changedAt) {
		if (status != NotificationStatus.PENDING && status != NotificationStatus.RETRY_PENDING) {
			throw new IllegalStateException("대기 중인 알림만 발송 시작할 수 있습니다.");
		}
		Objects.requireNonNull(changedAt, "변경 시각은 null일 수 없습니다.");
		if (nextAttemptAt != null && changedAt.isBefore(nextAttemptAt)) {
			throw new IllegalStateException("다음 발송 가능 시각 전에는 알림을 시작할 수 없습니다.");
		}
		return withStatus(NotificationStatus.SENDING, attemptCount + 1, null, null, null, null, changedAt);
	}

	public NotificationRequest markSent(Instant sentAt) {
		requireStatus(NotificationStatus.SENDING, "발송 중인 알림만 성공 처리할 수 있습니다.");
		Objects.requireNonNull(sentAt, "알림 발송 성공 시각은 null일 수 없습니다.");
		return withStatus(NotificationStatus.SENT, attemptCount, null, sentAt, null, null, sentAt);
	}

	public NotificationRequest markFailed(String failureCode, String failureMessage, Instant failedAt, Duration retryDelay) {
		requireStatus(NotificationStatus.SENDING, "발송 중인 알림만 실패 처리할 수 있습니다.");
		Objects.requireNonNull(failedAt, "알림 발송 실패 시각은 null일 수 없습니다.");
		Objects.requireNonNull(retryDelay, "알림 재시도 지연 시간은 null일 수 없습니다.");
		if (retryDelay.isNegative()) {
			throw new IllegalArgumentException("알림 재시도 지연 시간은 음수일 수 없습니다.");
		}
		if (attemptCount < maxAttempts) {
			return withStatus(NotificationStatus.RETRY_PENDING, attemptCount, failedAt.plus(retryDelay), null,
				failureCode, failureMessage, failedAt);
		}
		return withStatus(NotificationStatus.FAILED, attemptCount, null, null, failureCode, failureMessage, failedAt);
	}

	public NotificationRequest cancelAsSuperseded(Instant changedAt) {
		if (!isActive()) {
			throw new IllegalStateException("활성 알림만 정정 알림으로 대체 취소할 수 있습니다.");
		}
		return withStatus(NotificationStatus.CANCELED, attemptCount, null, null, "SUPERSEDED_BY_CORRECTION",
			"정정 알림으로 대체되었습니다.", changedAt);
	}

	private NotificationRequest withStatus(
		NotificationStatus nextStatus,
		int nextAttemptCount,
		Instant nextAttemptAt,
		Instant nextSentAt,
		String nextFailureCode,
		String nextFailureMessage,
		Instant changedAt
	) {
		Objects.requireNonNull(changedAt, "변경 시각은 null일 수 없습니다.");
		if (changedAt.isBefore(timestamps.updatedAt())) {
			throw new IllegalArgumentException("변경 시각은 기존 updatedAt보다 이전일 수 없습니다.");
		}
		return new NotificationRequest(id, notificationTargetId, childProfileId, ownerId, notificationDate, channel,
			reason, dedupKey, correctionKey, contentVersion, correction, supersedesNotificationId, nextStatus,
			nextAttemptCount, maxAttempts, nextAttemptAt, nextSentAt, nextFailureCode, nextFailureMessage,
			new EntityTimestamps(timestamps.createdAt(), changedAt));
	}

	private void requireStatus(NotificationStatus requiredStatus, String message) {
		if (status != requiredStatus) {
			throw new IllegalStateException(message);
		}
	}

	private static void validateStatusFields(
		NotificationStatus status,
		int attemptCount,
		Instant nextAttemptAt,
		Instant sentAt,
		String failureCode,
		String failureMessage
	) {
		boolean hasFailure = failureCode != null || failureMessage != null;
		switch (status) {
			case PENDING -> {
				if (attemptCount != 0 || nextAttemptAt == null || sentAt != null || hasFailure) {
					throw new IllegalArgumentException("신규 대기 알림의 상태 필드가 올바르지 않습니다.");
				}
			}
			case SENDING -> {
				if (attemptCount < 1 || nextAttemptAt != null || sentAt != null || hasFailure) {
					throw new IllegalArgumentException("발송 중 알림의 상태 필드가 올바르지 않습니다.");
				}
			}
			case RETRY_PENDING -> {
				if (attemptCount < 1 || nextAttemptAt == null || sentAt != null || failureCode == null) {
					throw new IllegalArgumentException("재시도 대기 알림의 상태 필드가 올바르지 않습니다.");
				}
			}
			case SENT -> {
				if (attemptCount < 1 || nextAttemptAt != null || sentAt == null || hasFailure) {
					throw new IllegalArgumentException("발송 성공 알림의 상태 필드가 올바르지 않습니다.");
				}
			}
			case FAILED, CANCELED -> {
				if (nextAttemptAt != null || sentAt != null || failureCode == null) {
					throw new IllegalArgumentException("종료된 실패 알림의 상태 필드가 올바르지 않습니다.");
				}
			}
		}
	}

	private static String requireSha256Hex(String value, String fieldName) {
		Objects.requireNonNull(value, fieldName + "는 null일 수 없습니다.");
		if (!value.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException(fieldName + "는 SHA-256 hex 형식이어야 합니다.");
		}
		return value;
	}

	private static String normalizeNullable(String value, int maxLength, String fieldName) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		if (normalized.isEmpty()) {
			return null;
		}
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(fieldName + "는 " + maxLength + "자를 초과할 수 없습니다.");
		}
		return normalized;
	}
}
