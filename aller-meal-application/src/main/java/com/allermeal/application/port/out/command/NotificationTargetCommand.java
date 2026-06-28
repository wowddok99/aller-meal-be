package com.allermeal.application.port.out.command;

import com.allermeal.application.notification.NotificationTargetReason;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.risk.MealRiskLevel;
import com.allermeal.domain.school.SchoolId;
import com.allermeal.domain.user.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

public record NotificationTargetCommand(
	UUID notificationTargetId,
	ChildProfileId childProfileId,
	UserId ownerId,
	SchoolId schoolId,
	LocalDate notificationDate,
	LocalTime notificationTime,
	String timezone,
	NotificationTargetReason reason,
	MealRiskLevel riskLevel,
	String riskVersion,
	int mealCount,
	Instant createdAt
) {

	public NotificationTargetCommand {
		Objects.requireNonNull(notificationTargetId, "알림 대상 ID는 null일 수 없습니다.");
		Objects.requireNonNull(childProfileId, "자녀 ID는 null일 수 없습니다.");
		Objects.requireNonNull(ownerId, "소유자 ID는 null일 수 없습니다.");
		Objects.requireNonNull(schoolId, "학교 ID는 null일 수 없습니다.");
		Objects.requireNonNull(notificationDate, "알림 대상 날짜는 null일 수 없습니다.");
		Objects.requireNonNull(notificationTime, "알림 시각은 null일 수 없습니다.");
		if (!"Asia/Seoul".equals(timezone)) {
			throw new IllegalArgumentException("알림 대상 timezone은 Asia/Seoul이어야 합니다.");
		}
		Objects.requireNonNull(reason, "알림 대상 사유는 null일 수 없습니다.");
		if (reason == NotificationTargetReason.NO_MEAL && riskLevel != null) {
			throw new IllegalArgumentException("급식 없음 알림 대상은 위험도를 저장하지 않습니다.");
		}
		if (reason != NotificationTargetReason.NO_MEAL && riskLevel == null) {
			throw new IllegalArgumentException("급식 있음 알림 대상은 위험도가 필요합니다.");
		}
		if (riskVersion != null && !riskVersion.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("알림 대상 위험도 version은 SHA-256 hex 형식이어야 합니다.");
		}
		if (mealCount < 0) {
			throw new IllegalArgumentException("알림 대상 급식 수는 음수일 수 없습니다.");
		}
		Objects.requireNonNull(createdAt, "알림 대상 생성 시각은 null일 수 없습니다.");
	}
}
