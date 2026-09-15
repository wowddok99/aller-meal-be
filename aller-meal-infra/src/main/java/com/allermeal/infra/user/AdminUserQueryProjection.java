package com.allermeal.infra.user;

import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.time.Instant;
import java.util.UUID;

public interface AdminUserQueryProjection {

	UUID getUserId();

	String getEncryptedEmail();

	UserRole getRole();

	UserStatus getStatus();

	EmailVerificationStatus getEmailVerificationStatus();

	Instant getCreatedAt();

	Instant getWithdrawalDueAt();

	Long getVersion();
}
