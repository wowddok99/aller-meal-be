package com.allermeal.application.admin;

import com.allermeal.application.port.out.AdminAuditLogRepository;
import com.allermeal.application.port.out.AdminRecollectionRequestRepository;
import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.ExternalApiLogRepository;
import com.allermeal.application.port.out.MealCollectionDispatcher;
import com.allermeal.application.port.out.command.AdminAuditLogCommand;
import com.allermeal.application.port.out.command.AdminRecollectionRequestCommand;
import com.allermeal.application.port.out.result.AdminRecollectionRequestResult;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class AdminCollectionFailureService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 200;

	private final CollectionJobRepository collectionJobRepository;
	private final ExternalApiLogRepository externalApiLogRepository;
	private final AdminRecollectionRequestRepository recollectionRequestRepository;
	private final MealCollectionDispatcher collectionDispatcher;
	private final AdminAuditLogRepository auditLogRepository;
	private final Clock clock;

	public AdminCollectionFailureService(
		CollectionJobRepository collectionJobRepository,
		ExternalApiLogRepository externalApiLogRepository,
		AdminRecollectionRequestRepository recollectionRequestRepository,
		MealCollectionDispatcher collectionDispatcher,
		AdminAuditLogRepository auditLogRepository,
		Clock clock
	) {
		this.collectionJobRepository = collectionJobRepository;
		this.externalApiLogRepository = externalApiLogRepository;
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

	@Transactional
	public AdminRecollectionResult requestRecollection(
		User actor,
		CollectionJobId failedCollectionJobId,
		String idempotencyKey
	) {
		requireAdmin(actor);
		String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
		CollectionJob failedJob = collectionJobRepository.findById(failedCollectionJobId)
			.filter(job -> job.status() == CollectionJobStatus.FAILED)
			.orElseThrow(AdminCollectionJobNotFoundException::new);
		AdminRecollectionRequestResult existing = recollectionRequestRepository
			.findByIdempotencyKey(normalizedKey, failedJob.id())
			.orElse(null);
		if (existing != null) {
			if (existing.conflict()) {
				throw new AdminRecollectionConflictException();
			}
			CollectionJob existingJob = collectionJobRepository.findById(existing.collectionJobId())
				.orElseThrow(AdminCollectionJobNotFoundException::new);
			return new AdminRecollectionResult(
				failedJob.id(), existing.collectionJobId(), existingJob.status(), true);
		}
		Instant requestedAt = clock.instant();
		CollectionJob pending = CollectionJob.pending(
			new CollectionJobId(UUID.randomUUID()),
			failedJob.schoolId(),
			failedJob.mealDate(),
			failedJob.mealType(),
			requestedAt);
		CollectionJob active = collectionJobRepository.createOrGetActive(pending, requestedAt);
		AdminRecollectionRequestResult saved = recollectionRequestRepository.save(new AdminRecollectionRequestCommand(
			UUID.randomUUID(),
			normalizedKey,
			actor.id(),
			failedJob.id(),
			active.id(),
			requestedAt));
		if (saved.conflict()) {
			throw new AdminRecollectionConflictException();
		}
		if (!saved.duplicate()) {
			auditLogRepository.save(new AdminAuditLogCommand(
				UUID.randomUUID(),
				actor.id(),
				actor.id(),
				"REQUEST_COLLECTION_RETRY",
				"SUCCEEDED",
				"originalCollectionJobId=%s collectionJobId=%s duplicate=false".formatted(
					failedJob.id().value(), saved.collectionJobId().value()),
				requestedAt));
		}
		if (!saved.duplicate() && active.status() == CollectionJobStatus.PENDING) {
			dispatchAfterCommit(active);
		}
		return new AdminRecollectionResult(
			failedJob.id(), saved.collectionJobId(), active.status(), saved.duplicate());
	}

	private void validatePage(int page, int pageSize) {
		if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE
			|| (long) page > ((long) Integer.MAX_VALUE / pageSize) + 1) {
			throw new AdminInvalidCollectionRequestException();
		}
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
