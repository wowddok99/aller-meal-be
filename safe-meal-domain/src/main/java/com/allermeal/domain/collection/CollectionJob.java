package com.allermeal.domain.collection;

import com.allermeal.domain.common.EntityTimestamps;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record CollectionJob(
	CollectionJobId id,
	SchoolId schoolId,
	LocalDate mealDate,
	MealType mealType,
	CollectionJobStatus status,
	Long responseTimeMillis,
	Long collectionDurationMillis,
	Instant leaseUntil,
	UUID rawObjectId,
	String failureCode,
	String failureMessage,
	EntityTimestamps timestamps
) {

	public CollectionJob {
		Objects.requireNonNull(id, "수집 작업 ID는 null일 수 없습니다.");
		Objects.requireNonNull(schoolId, "학교 ID는 null일 수 없습니다.");
		Objects.requireNonNull(mealDate, "수집 대상 날짜는 null일 수 없습니다.");
		Objects.requireNonNull(mealType, "수집 대상 식사 타입은 null일 수 없습니다.");
		Objects.requireNonNull(status, "수집 작업 상태는 null일 수 없습니다.");
		if (responseTimeMillis != null && responseTimeMillis < 0) {
			throw new IllegalArgumentException("응답 시간은 0 이상이어야 합니다.");
		}
		if (collectionDurationMillis != null && collectionDurationMillis < 0) {
			throw new IllegalArgumentException("수집 처리 시간은 0 이상이어야 합니다.");
		}
		failureCode = normalizeNullable(failureCode, 100, "실패 code");
		failureMessage = normalizeNullable(failureMessage, 1000, "실패 메시지");
		Objects.requireNonNull(timestamps, "수집 작업 시각 정보는 null일 수 없습니다.");
		boolean terminal = status == CollectionJobStatus.SUCCEEDED || status == CollectionJobStatus.FAILED;
		if (terminal != (responseTimeMillis != null)) {
			throw new IllegalArgumentException("종료된 수집 작업에만 응답 시간이 필요합니다.");
		}
		if (terminal != (collectionDurationMillis != null)) {
			throw new IllegalArgumentException("종료된 수집 작업에만 수집 처리 시간이 필요합니다.");
		}
		if ((status == CollectionJobStatus.RUNNING) != (leaseUntil != null)) {
			throw new IllegalArgumentException("RUNNING 수집 작업에만 lease가 필요합니다.");
		}
		if (!terminal && rawObjectId != null) {
			throw new IllegalArgumentException("종료된 수집 작업에만 원본 객체를 연결할 수 있습니다.");
		}
		if (status == CollectionJobStatus.FAILED && failureCode == null) {
			throw new IllegalArgumentException("실패한 수집 작업에는 failureCode가 필요합니다.");
		}
		if (status != CollectionJobStatus.FAILED && (failureCode != null || failureMessage != null)) {
			throw new IllegalArgumentException("실패하지 않은 수집 작업에는 실패 정보를 저장할 수 없습니다.");
		}
	}

	public static CollectionJob pending(
		CollectionJobId id,
		SchoolId schoolId,
		LocalDate mealDate,
		MealType mealType,
		Instant createdAt
	) {
		return new CollectionJob(id, schoolId, mealDate, mealType, CollectionJobStatus.PENDING,
			null, null, null, null, null, null, EntityTimestamps.createdAt(createdAt));
	}

	public boolean isActive() {
		return status == CollectionJobStatus.PENDING || status == CollectionJobStatus.RUNNING;
	}

	public CollectionJob start(Instant changedAt, Instant leaseUntil) {
		requireStatus(CollectionJobStatus.PENDING, "PENDING 수집 작업만 시작할 수 있습니다.");
		Objects.requireNonNull(leaseUntil, "lease 만료 시각은 null일 수 없습니다.");
		if (!leaseUntil.isAfter(changedAt)) {
			throw new IllegalArgumentException("lease 만료 시각은 시작 시각 이후여야 합니다.");
		}
		return withResult(CollectionJobStatus.RUNNING, null, null, leaseUntil, null, null, null, changedAt);
	}

	public CollectionJob succeed(
		long responseTimeMillis,
		long collectionDurationMillis,
		UUID rawObjectId,
		Instant changedAt
	) {
		requireStatus(CollectionJobStatus.RUNNING, "RUNNING 수집 작업만 성공 처리할 수 있습니다.");
		return withResult(CollectionJobStatus.SUCCEEDED, responseTimeMillis, collectionDurationMillis,
			null, Objects.requireNonNull(rawObjectId, "성공 수집 원본 ID는 null일 수 없습니다."), null, null, changedAt);
	}

	public CollectionJob fail(
		long responseTimeMillis,
		long collectionDurationMillis,
		UUID rawObjectId,
		String failureCode,
		String failureMessage,
		Instant changedAt
	) {
		if (!isActive()) {
			throw new IllegalStateException("활성 수집 작업만 실패 처리할 수 있습니다.");
		}
		return withResult(CollectionJobStatus.FAILED, responseTimeMillis, collectionDurationMillis,
			null, rawObjectId, failureCode, failureMessage, changedAt);
	}

	private CollectionJob withResult(
		CollectionJobStatus nextStatus,
		Long nextResponseTimeMillis,
		Long nextCollectionDurationMillis,
		Instant nextLeaseUntil,
		UUID nextRawObjectId,
		String nextFailureCode,
		String nextFailureMessage,
		Instant changedAt
	) {
		Objects.requireNonNull(changedAt, "변경 시각은 null일 수 없습니다.");
		if (changedAt.isBefore(timestamps.updatedAt())) {
			throw new IllegalArgumentException("변경 시각은 기존 updatedAt보다 이전일 수 없습니다.");
		}
		return new CollectionJob(id, schoolId, mealDate, mealType, nextStatus, nextResponseTimeMillis,
			nextCollectionDurationMillis, nextLeaseUntil, nextRawObjectId, nextFailureCode, nextFailureMessage,
			new EntityTimestamps(timestamps.createdAt(), changedAt));
	}

	private void requireStatus(CollectionJobStatus requiredStatus, String message) {
		if (status != requiredStatus) {
			throw new IllegalStateException(message);
		}
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
			throw new IllegalArgumentException(fieldName + "은 " + maxLength + "자를 초과할 수 없습니다.");
		}
		return normalized;
	}
}
