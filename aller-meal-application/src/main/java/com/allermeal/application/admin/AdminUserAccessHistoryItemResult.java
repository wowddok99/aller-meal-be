package com.allermeal.application.admin;

import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminUserAccessHistoryItemResult(
	UUID eventId,
	UserId actorUserId,
	String action,
	UserRole beforeRole,
	UserRole afterRole,
	UserStatus beforeStatus,
	UserStatus afterStatus,
	String reason,
	Instant createdAt,
	String source
) {
}
