package com.allermeal.api.auth.response;

import java.util.UUID;

public record RefreshSessionStatusResponse(String sessionId, UUID userId) {
}
