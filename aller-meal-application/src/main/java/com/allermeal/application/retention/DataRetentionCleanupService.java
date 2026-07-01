package com.allermeal.application.retention;

import com.allermeal.application.port.out.OperationLogRetentionRepository;
import com.allermeal.application.port.out.RawPayloadObjectRemover;
import com.allermeal.application.port.out.RawPayloadRetentionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class DataRetentionCleanupService {

	private static final System.Logger log = System.getLogger(DataRetentionCleanupService.class.getName());

	private final RawPayloadRetentionRepository rawPayloadRetentionRepository;
	private final RawPayloadObjectRemover rawPayloadObjectRemover;
	private final OperationLogRetentionRepository operationLogRetentionRepository;
	private final Clock clock;
	private final Duration rawPayloadRetentionPeriod;
	private final Duration operationLogRetentionPeriod;
	private final int rawPayloadBatchSize;
	private final int operationLogBatchSize;

	public DataRetentionCleanupService(
		RawPayloadRetentionRepository rawPayloadRetentionRepository,
		RawPayloadObjectRemover rawPayloadObjectRemover,
		OperationLogRetentionRepository operationLogRetentionRepository,
		Clock clock,
		Duration rawPayloadRetentionPeriod,
		Duration operationLogRetentionPeriod,
		int rawPayloadBatchSize,
		int operationLogBatchSize
	) {
		this.rawPayloadRetentionRepository = Objects.requireNonNull(rawPayloadRetentionRepository,
			"RawPayloadRetentionRepository는 null일 수 없습니다.");
		this.rawPayloadObjectRemover = Objects.requireNonNull(rawPayloadObjectRemover,
			"RawPayloadObjectRemover는 null일 수 없습니다.");
		this.operationLogRetentionRepository = Objects.requireNonNull(operationLogRetentionRepository,
			"OperationLogRetentionRepository는 null일 수 없습니다.");
		this.clock = Objects.requireNonNull(clock, "Clock은 null일 수 없습니다.");
		this.rawPayloadRetentionPeriod = requirePositive(rawPayloadRetentionPeriod, "원본 payload 보존 기간");
		this.operationLogRetentionPeriod = requirePositive(operationLogRetentionPeriod, "운영 로그 보존 기간");
		if (rawPayloadBatchSize <= 0) {
			throw new IllegalArgumentException("원본 payload 삭제 batch size는 1 이상이어야 합니다.");
		}
		if (operationLogBatchSize <= 0) {
			throw new IllegalArgumentException("운영 로그 삭제 batch size는 1 이상이어야 합니다.");
		}
		this.rawPayloadBatchSize = rawPayloadBatchSize;
		this.operationLogBatchSize = operationLogBatchSize;
	}

	public DataRetentionCleanupResult cleanupExpiredData() {
		Instant now = clock.instant();
		Instant rawPayloadExpiresBefore = now.minus(rawPayloadRetentionPeriod);
		Instant operationLogCreatedBefore = now.minus(operationLogRetentionPeriod);

		int rawPayloadScannedCount = 0;
		int rawPayloadDeletedCount = 0;
		int rawPayloadFailedCount = 0;
		for (var rawPayload : rawPayloadRetentionRepository.findExpiredRawPayloads(
			rawPayloadExpiresBefore, rawPayloadBatchSize)) {
			rawPayloadScannedCount++;
			try {
				rawPayloadObjectRemover.remove(rawPayload.objectKey());
			} catch (RuntimeException exception) {
				rawPayloadFailedCount++;
				log.log(System.Logger.Level.WARNING,
					"원본 payload 객체 삭제에 실패했습니다. rawObjectId=%s, objectKey=%s, expiresAt=%s"
						.formatted(rawPayload.rawObjectId(), rawPayload.objectKey(), rawPayload.expiresAt()),
					exception);
				continue;
			}
			try {
				if (rawPayloadRetentionRepository.deleteMetadata(rawPayload.rawObjectId(), rawPayload.expiresAt())) {
					rawPayloadDeletedCount++;
				} else {
					rawPayloadFailedCount++;
					log.log(System.Logger.Level.WARNING,
						"원본 payload metadata 삭제에 실패했습니다. rawObjectId={0}, objectKey={1}, expiresAt={2}",
						rawPayload.rawObjectId(), rawPayload.objectKey(), rawPayload.expiresAt());
				}
			} catch (RuntimeException exception) {
				rawPayloadFailedCount++;
				log.log(System.Logger.Level.WARNING,
					"원본 payload metadata 삭제 중 예외가 발생했습니다. rawObjectId=%s, objectKey=%s, expiresAt=%s"
						.formatted(rawPayload.rawObjectId(), rawPayload.objectKey(), rawPayload.expiresAt()),
					exception);
			}
		}

		OperationLogRetentionDeletionResult operationLogDeletion =
			operationLogRetentionRepository.deleteExpiredNonPersonalLogs(operationLogCreatedBefore, operationLogBatchSize);
		return new DataRetentionCleanupResult(
			rawPayloadExpiresBefore,
			operationLogCreatedBefore,
			rawPayloadScannedCount,
			rawPayloadDeletedCount,
			rawPayloadFailedCount,
			operationLogDeletion);
	}

	private Duration requirePositive(Duration value, String name) {
		Objects.requireNonNull(value, name + "은 null일 수 없습니다.");
		if (value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException(name + "은 양수여야 합니다.");
		}
		return value;
	}
}
