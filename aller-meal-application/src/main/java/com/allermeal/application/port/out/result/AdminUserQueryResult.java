package com.allermeal.application.port.out.result;

import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.EncryptedEmail;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.time.Instant;

public record AdminUserQueryResult(
	UserId userId,
	EncryptedEmail encryptedEmail,
	UserRole role,
	UserStatus status,
	EmailVerificationStatus emailVerificationStatus,
	Instant createdAt,
	Instant withdrawalDueAt,
	long version
) {
}
