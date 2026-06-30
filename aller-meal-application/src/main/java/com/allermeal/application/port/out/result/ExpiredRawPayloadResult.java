package com.allermeal.application.port.out.result;

import java.time.Instant;
import java.util.UUID;

public record ExpiredRawPayloadResult(
	UUID rawObjectId,
	String objectKey,
	Instant expiresAt
) {
}
