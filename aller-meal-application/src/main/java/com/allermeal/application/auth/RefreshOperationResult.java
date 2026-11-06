package com.allermeal.application.auth;

import java.time.Instant;

public record RefreshOperationResult(String operationId, String sessionId, Instant expiresAt) {
}
