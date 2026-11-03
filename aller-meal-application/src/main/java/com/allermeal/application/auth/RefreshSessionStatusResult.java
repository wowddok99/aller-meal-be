package com.allermeal.application.auth;

import java.util.UUID;

public record RefreshSessionStatusResult(String sessionId, UUID userId) {
}
