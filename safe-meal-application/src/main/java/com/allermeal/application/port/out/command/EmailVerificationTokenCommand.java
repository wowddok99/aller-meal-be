package com.allermeal.application.port.out.command;

import com.allermeal.domain.user.UserId;
import java.time.Duration;

public record EmailVerificationTokenCommand(UserId userId, String tokenHash, Duration ttl) {
}
