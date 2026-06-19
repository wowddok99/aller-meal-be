package com.allermeal.application.port.out.command;

import com.allermeal.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;

public record RefreshTokenCommand(UserId userId, String tokenHash, Instant expiresAt, Duration ttl) {
}
