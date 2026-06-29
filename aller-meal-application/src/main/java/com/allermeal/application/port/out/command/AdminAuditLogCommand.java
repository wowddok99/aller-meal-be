package com.allermeal.application.port.out.command;

import com.allermeal.domain.user.UserId;
import java.time.Instant;
import java.util.UUID;

public record AdminAuditLogCommand(
	UUID auditLogId,
	UserId actorUserId,
	UserId targetUserId,
	String action,
	String outcome,
	String detail,
	Instant createdAt
) {
}
