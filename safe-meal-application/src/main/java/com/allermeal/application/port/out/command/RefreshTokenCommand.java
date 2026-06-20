package com.allermeal.application.port.out.command;

import com.allermeal.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;

public record RefreshTokenCommand(
	UserId userId,
	String familyId,
	String tokenHash,
	Instant issuedAt,
	Instant expiresAt,
	Duration ttl
) {
}
