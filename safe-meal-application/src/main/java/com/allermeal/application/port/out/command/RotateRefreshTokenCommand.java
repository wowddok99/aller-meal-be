package com.allermeal.application.port.out.command;

import java.time.Duration;
import java.time.Instant;

public record RotateRefreshTokenCommand(String oldTokenHash, String newTokenHash, Instant expiresAt, Duration ttl) {
}
