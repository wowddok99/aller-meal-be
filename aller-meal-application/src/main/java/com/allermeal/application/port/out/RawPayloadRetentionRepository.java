package com.allermeal.application.port.out;

import com.allermeal.application.port.out.result.ExpiredRawPayloadResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RawPayloadRetentionRepository {

	List<ExpiredRawPayloadResult> findExpiredRawPayloads(Instant expiresBeforeInclusive, int limit);

	boolean deleteMetadata(UUID rawObjectId, Instant selectedExpiresAt);
}
