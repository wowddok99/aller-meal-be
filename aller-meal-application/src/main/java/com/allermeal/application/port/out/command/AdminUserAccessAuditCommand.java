package com.allermeal.application.port.out.command;

import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AdminUserAccessAuditCommand(
	UUID eventId,
	UserId actorUserId,
	UserId targetUserId,
	String action,
	UserRole beforeRole,
	UserRole afterRole,
	UserStatus beforeStatus,
	UserStatus afterStatus,
	String reason,
	Instant createdAt
) {

	public AdminUserAccessAuditCommand {
		Objects.requireNonNull(eventId, "사용자 접근 감사 eventId는 null일 수 없습니다.");
		Objects.requireNonNull(actorUserId, "사용자 접근 감사 actorUserId는 null일 수 없습니다.");
		Objects.requireNonNull(targetUserId, "사용자 접근 감사 targetUserId는 null일 수 없습니다.");
		Objects.requireNonNull(action, "사용자 접근 감사 action은 null일 수 없습니다.");
		Objects.requireNonNull(beforeRole, "사용자 접근 감사 beforeRole은 null일 수 없습니다.");
		Objects.requireNonNull(afterRole, "사용자 접근 감사 afterRole은 null일 수 없습니다.");
		Objects.requireNonNull(beforeStatus, "사용자 접근 감사 beforeStatus는 null일 수 없습니다.");
		Objects.requireNonNull(afterStatus, "사용자 접근 감사 afterStatus는 null일 수 없습니다.");
		Objects.requireNonNull(reason, "사용자 접근 감사 reason은 null일 수 없습니다.");
		Objects.requireNonNull(createdAt, "사용자 접근 감사 createdAt은 null일 수 없습니다.");
		reason = reason.trim();
		if (reason.isBlank() || reason.length() > 500) {
			throw new IllegalArgumentException("사용자 접근 감사 reason은 공백이 아니고 500자 이하여야 합니다.");
		}
	}
}
