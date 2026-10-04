package com.allermeal.application.admin;

import com.allermeal.application.port.out.AdminAuditLogRepository;
import com.allermeal.application.port.out.AdminRecollectionRequestRepository;
import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.ExternalApiLogRepository;
import com.allermeal.application.port.out.MealCollectionDispatcher;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.command.AdminAuditLogCommand;
import com.allermeal.application.port.out.command.AdminRecollectionRequestCommand;
import com.allermeal.application.port.out.result.AdminCollectionRecoveryLookupResult;
import com.allermeal.application.port.out.result.AdminRecollectionRequestResult;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.meal.MealItemLabelingStatus;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class AdminCollectionFailureService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 200;

	private final CollectionJobRepository collectionJobRepository;
	private final ExternalApiLogRepository externalApiLogRepository;
	private final MealRepository mealRepository;
	private final AdminRecollectionRequestRepository recollectionRequestRepository;
	private final MealCollectionDispatcher collectionDispatcher;
	private final AdminAuditLogRepository auditLogRepository;
	private final Clock clock;
	private final Duration pendingRecoveryDelay;

	public AdminCollectionFailureService(
		CollectionJobRepository collectionJobRepository,
		ExternalApiLogRepository externalApiLogRepository,
		MealRepository mealRepository,
		AdminRecollectionRequestRepository recollectionRequestRepository,
		MealCollectionDispatcher collectionDispatcher,
		AdminAuditLogRepository auditLogRepository,
		Clock clock
	) {
		this(collectionJobRepository, externalApiLogRepository, mealRepository, recollectionRequestRepository,
			collectionDispatcher, auditLogRepository, clock, Duration.ofMinutes(2));
	}

	public AdminCollectionFailureService(CollectionJobRepository collectionJobRepository,
		ExternalApiLogRepository externalApiLogRepository, MealRepository mealRepository,
		AdminRecollectionRequestRepository recollectionRequestRepository, MealCollectionDispatcher collectionDispatcher,
		AdminAuditLogRepository auditLogRepository, Clock clock, Duration pendingRecoveryDelay) {
		if (pendingRecoveryDelay.isZero() || pendingRecoveryDelay.isNegative()) {
			throw new IllegalArgumentException("대기 작업 복구 지연은 양수여야 합니다.");
		}
		this.pendingRecoveryDelay = pendingRecoveryDelay;
		this.collectionJobRepository = collectionJobRepository;
		this.externalApiLogRepository = externalApiLogRepository;
		this.mealRepository = mealRepository;
		this.recollectionRequestRepository = recollectionRequestRepository;
		this.collectionDispatcher = collectionDispatcher;
		this.auditLogRepository = auditLogRepository;
		this.clock = clock;
	}

	public AdminFailedCollectionJobPageResult findFailedCollectionJobs(User actor, int page, int pageSize) {
		requireAdmin(actor);
		validatePage(page, pageSize);
		return collectionJobRepository.findFailed(page, pageSize);
	}

	public AdminExternalApiLogPageResult findExternalApiLogs(User actor, int page, int pageSize) {
		requireAdmin(actor);
		validatePage(page, pageSize);
		return externalApiLogRepository.findRecent(page, pageSize);
	}

	public AdminExternalApiLogPageResult findExternalApiLogs(
		User actor, int page, int pageSize, String provider, String method, String outcome, String query
	) {
		requireAdmin(actor);
		validatePage(page, pageSize);
		return externalApiLogRepository.findAdminPage(new AdminExternalApiLogQuery(
			page, pageSize, normalizeOptional(provider), normalizeOptional(method), normalizeOptional(outcome), normalizeQuery(query)));
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public AdminCollectionJobPageResult findCollectionJobs(
		User actor, int page, int pageSize, String status, String schoolId, String mealDate, String mealType, String query
	) {
		return findCollectionJobs(actor, page, pageSize, status, schoolId, mealDate, mealType, query, null);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public AdminCollectionJobPageResult findCollectionJobs(User actor, int page, int pageSize, String status,
		String schoolId, String mealDate, String mealType, String query, Boolean unresolvedFailure) {
		requireAdmin(actor);
		validatePage(page, pageSize);
		AdminCollectionJobPageResult result = collectionJobRepository.findAdminPage(new AdminCollectionJobQuery(
			page, pageSize, parseEnum(status, CollectionJobStatus.class), parseUuid(schoolId), parseDate(mealDate),
			parseEnum(mealType, MealType.class), normalizeQuery(query), unresolvedFailure));
		return new AdminCollectionJobPageResult(enrich(result.items()), result.page(), result.pageSize(), result.totalCount());
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public AdminCollectionJobItemResult findCollectionJob(User actor, CollectionJobId id) {
		requireAdmin(actor);
		return enrich(List.of(collectionJobRepository.findAdminById(id)
			.orElseThrow(AdminCollectionJobNotFoundException::new))).getFirst();
	}

	private List<AdminCollectionJobItemResult> enrich(List<AdminCollectionJobItemResult> items) {
		Instant now = clock.instant();
		var summaries = recollectionRequestRepository.findRecoveries(items.stream().map(i -> i.collectionJobId().value()).toList(), now);
		return items.stream().map(item -> {
			var summary = summaries.get(item.collectionJobId().value());
			if (summary == null) throw new IllegalStateException("수집 복구 요약이 없습니다.");
			return item.withRecovery(summary.unresolvedFailure(), summary.recovery(), actions(item.status(), item.createdAt(), summary, now));
		}).toList();
	}

	private AdminCollectionAvailableActionsResult actions(CollectionJobStatus status, Instant createdAt,
		AdminCollectionRecoveryLookupResult summary, Instant now) {
		Instant availableAt = null;
		if (status == CollectionJobStatus.PENDING) {
			Instant base = summary.latestExecutionAt() != null && summary.latestExecutionAt().isAfter(createdAt)
				? summary.latestExecutionAt() : createdAt;
			availableAt = base.plus(pendingRecoveryDelay);
		}
		return new AdminCollectionAvailableActionsResult(status == CollectionJobStatus.FAILED && summary.unresolvedFailure()
			&& !summary.hasActive(), availableAt != null && !now.isBefore(availableAt), availableAt);
	}

	public AdminMealItemLabelingPageResult findMealItemLabelings(
		User actor, int page, int pageSize, String status, String schoolId, String mealDate, String mealType, String query
	) {
		requireAdmin(actor);
		validatePage(page, pageSize);
		return mealRepository.findAdminMealItemLabelings(new AdminMealItemLabelingQuery(
			page, pageSize, parseEnum(status, MealItemLabelingStatus.class), parseUuid(schoolId), parseDate(mealDate),
			parseEnum(mealType, MealType.class), normalizeQuery(query)));
	}

	@Transactional
	public AdminRecollectionResult requestRecollection(User actor, CollectionJobId id, String idempotencyKey) {
		return requestCollectionAction(actor, id, idempotencyKey, AdminCollectionRequestType.RECOLLECTION);
	}

	@Transactional
	public AdminRecollectionResult requestExecution(User actor, CollectionJobId id, String idempotencyKey) {
		return requestCollectionAction(actor, id, idempotencyKey, AdminCollectionRequestType.EXECUTION);
	}

	private AdminRecollectionResult requestCollectionAction(User actor, CollectionJobId id, String key,
		AdminCollectionRequestType type) {
		requireAdmin(actor);
		String normalizedKey = normalizeIdempotencyKey(key);
		recollectionRequestRepository.lockRequestKey(normalizedKey);
		var replay = recollectionRequestRepository.findByIdempotencyKey(normalizedKey, id, actor.id(), type).orElse(null);
		if (replay != null) {
			if (replay.conflict()) throw new AdminRecollectionConflictException();
			CollectionJob current = collectionJobRepository.findById(replay.collectionJobId())
				.orElseThrow(AdminCollectionJobNotFoundException::new);
			return new AdminRecollectionResult(id, current.id(), current.status(), true);
		}
		CollectionJob source = collectionJobRepository.findById(id).orElseThrow(AdminCollectionJobNotFoundException::new);
		// 두 관리자 액션 모두 수집 대상 잠금 다음에 원본 행을 잠급니다.
		recollectionRequestRepository.lockCollectionTarget(source);
		source = collectionJobRepository.findByIdForUpdate(id).orElseThrow(AdminCollectionJobNotFoundException::new);
		Instant requestedAt = clock.instant();
		var summary = recollectionRequestRepository.findRecoveries(List.of(id.value()), requestedAt).get(id.value());
		if (summary == null) throw new IllegalStateException("수집 복구 요약이 없습니다.");
		var available = actions(source.status(), source.timestamps().createdAt(), summary, requestedAt);
		if (type == AdminCollectionRequestType.RECOLLECTION ? !available.canRecollect() : !available.canExecute()) {
			throw new AdminCollectionJobStateConflictException();
		}
		CollectionJob target = source;
		if (type == AdminCollectionRequestType.RECOLLECTION) {
			target = collectionJobRepository.createOrGetActive(CollectionJob.pending(new CollectionJobId(UUID.randomUUID()),
				source.schoolId(), source.mealDate(), source.mealType(), requestedAt), requestedAt);
		}
		var saved = recollectionRequestRepository.save(new AdminRecollectionRequestCommand(UUID.randomUUID(), normalizedKey,
			actor.id(), source.id(), target.id(), requestedAt, type));
		if (saved.conflict()) throw new AdminRecollectionConflictException();
		if (saved.duplicate()) throw new IllegalStateException("잠금 이후 중복 수집 요청 발생");
		String action = type == AdminCollectionRequestType.EXECUTION ? "REQUEST_COLLECTION_EXECUTION" : "REQUEST_COLLECTION_RETRY";
		auditLogRepository.save(new AdminAuditLogCommand(UUID.randomUUID(), actor.id(), actor.id(), action, "SUCCEEDED",
			"requestAccepted=true action=%s originalCollectionJobId=%s collectionJobId=%s requestedAt=%s".formatted(
				type, source.id().value(), target.id().value(), requestedAt), requestedAt));
		if (target.status() == CollectionJobStatus.PENDING) dispatchAfterCommit(target);
		return new AdminRecollectionResult(source.id(), target.id(), target.status(), false);
	}

	private void validatePage(int page, int pageSize) {
		if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE
			|| (long) page > ((long) Integer.MAX_VALUE / pageSize) + 1) {
			throw new AdminInvalidCollectionRequestException();
		}
	}

	private <T extends Enum<T>> T parseEnum(String value, Class<T> type) {
		if (value == null || value.isBlank()) return null;
		try {
			return Enum.valueOf(type, value.trim());
		} catch (IllegalArgumentException exception) {
			throw new AdminInvalidCollectionRequestException();
		}
	}

	private UUID parseUuid(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			return UUID.fromString(value.trim());
		} catch (IllegalArgumentException exception) {
			throw new AdminInvalidCollectionRequestException();
		}
	}

	private LocalDate parseDate(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			return LocalDate.parse(value.trim());
		} catch (DateTimeParseException exception) {
			throw new AdminInvalidCollectionRequestException();
		}
	}

	private String normalizeQuery(String value) {
		if (value == null || value.isBlank()) return null;
		String normalized = value.trim();
		if (normalized.length() > 100) throw new AdminInvalidCollectionRequestException();
		return normalized;
	}

	private String normalizeOptional(String value) {
		if (value == null || value.isBlank()) return null;
		String normalized = value.trim();
		if (normalized.length() > 100) throw new AdminInvalidCollectionRequestException();
		return normalized;
	}

	private void dispatchAfterCommit(CollectionJob collectionJob) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			collectionDispatcher.dispatch(collectionJob);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				collectionDispatcher.dispatch(collectionJob);
			}
		});
	}

	private String normalizeIdempotencyKey(String value) {
		if (value == null) {
			throw new AdminInvalidCollectionRequestException();
		}
		String normalized = value.trim();
		if (normalized.isEmpty() || normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new AdminInvalidCollectionRequestException();
		}
		String upper = normalized.toUpperCase(Locale.ROOT);
		if (upper.contains(" ") || upper.contains("\t") || upper.contains("\n") || upper.contains("\r")) {
			throw new AdminInvalidCollectionRequestException();
		}
		return normalized;
	}

	private void requireAdmin(User actor) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new AdminAuthorizationException();
		}
	}
}
