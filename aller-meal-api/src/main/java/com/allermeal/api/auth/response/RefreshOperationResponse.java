package com.allermeal.api.auth.response;

import java.time.Instant;

public record RefreshOperationResponse(String operationId, String sessionId, Instant expiresAt) {
}
