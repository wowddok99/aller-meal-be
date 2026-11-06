package com.allermeal.application.port.out.command;

import java.time.Duration;
import java.time.Instant;

public record RotateRefreshTokenCommand(String oldTokenHash, String newTokenHash, Instant expiresAt, Duration ttl,
	Instant issuedAt, Duration sessionSlidingTtl, String operationId) {
	public RotateRefreshTokenCommand(String oldTokenHash, String newTokenHash, Instant expiresAt, Duration ttl,
		Instant issuedAt, Duration sessionSlidingTtl) {
		this(oldTokenHash, newTokenHash, expiresAt, ttl, issuedAt, sessionSlidingTtl, null);
	}
}
