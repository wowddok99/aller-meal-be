package com.allermeal.application.port.out;

import com.allermeal.application.admin.AdminCollectionRequestType;
import com.allermeal.application.port.out.command.AdminRecollectionRequestCommand;
import com.allermeal.application.port.out.result.AdminCollectionRecoveryLookupResult;
import com.allermeal.application.port.out.result.AdminRecollectionRequestResult;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.user.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AdminRecollectionRequestRepository {

	AdminRecollectionRequestResult save(AdminRecollectionRequestCommand command);

	Optional<AdminRecollectionRequestResult> findByIdempotencyKey(
		String idempotencyKey,
		CollectionJobId originalCollectionJobId
	);
	default void lockRequestKey(String key) { throw new UnsupportedOperationException("멱등 요청 잠금 미지원"); }
	default void lockCollectionTarget(CollectionJob job) { throw new UnsupportedOperationException("수집 대상 잠금 미지원"); }
	default Optional<AdminRecollectionRequestResult> findByIdempotencyKey(String key, CollectionJobId source,
		UserId actor, AdminCollectionRequestType type) { throw new UnsupportedOperationException("멱등 요청 fingerprint 조회 미지원"); }

	default Map<UUID, AdminCollectionRecoveryLookupResult> findRecoveries(Collection<UUID> jobIds, Instant now) {
		return findRecoveries(jobIds);
	}

	default Map<UUID, AdminCollectionRecoveryLookupResult> findRecoveries(Collection<UUID> jobIds) {
		return Map.of();
	}
}
