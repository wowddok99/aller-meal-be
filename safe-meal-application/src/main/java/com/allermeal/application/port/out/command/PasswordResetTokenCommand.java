package com.allermeal.application.port.out.command;

import com.allermeal.domain.user.UserId;
import java.time.Duration;

public record PasswordResetTokenCommand(UserId userId, String tokenHash, Duration ttl) {
}
