package com.allermeal.application.port.out.result;

import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.UserStatus;
import com.allermeal.domain.user.UserRole;
import java.time.Instant;

public record AccountProfileDataResult(String displayName, String encryptedEmail, UserStatus status,
	EmailVerificationStatus emailVerificationStatus, long sessionVersion, boolean personalDataDeleted, Instant withdrawalDueAt, UserRole role) {
	@Override
	public String toString() { return "AccountProfileDataResult[REDACTED]"; }
}
