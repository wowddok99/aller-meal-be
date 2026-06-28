package com.allermeal.application.port.out.result;

import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.notification.NotificationReason;
import com.allermeal.domain.risk.MealRiskLevel;
import com.allermeal.domain.user.UserId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record PendingNotificationTargetResult(
	UUID notificationTargetId,
	ChildProfileId childProfileId,
	UserId ownerId,
	LocalDate notificationDate,
	NotificationReason reason,
	MealRiskLevel riskLevel,
	String riskVersion,
	int mealCount
) {

	public PendingNotificationTargetResult {
		Objects.requireNonNull(notificationTargetId, "알림 대상 ID는 null일 수 없습니다.");
		Objects.requireNonNull(childProfileId, "자녀 ID는 null일 수 없습니다.");
		Objects.requireNonNull(ownerId, "소유자 ID는 null일 수 없습니다.");
		Objects.requireNonNull(notificationDate, "알림 날짜는 null일 수 없습니다.");
		Objects.requireNonNull(reason, "알림 사유는 null일 수 없습니다.");
		if (reason == NotificationReason.NO_MEAL && riskLevel != null) {
			throw new IllegalArgumentException("급식 없음 알림 대상은 위험도를 포함할 수 없습니다.");
		}
		if (reason != NotificationReason.NO_MEAL && riskLevel == null) {
			throw new IllegalArgumentException("급식 있음 알림 대상은 위험도가 필요합니다.");
		}
		if (riskVersion != null && !riskVersion.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("위험도 version은 SHA-256 hex 형식이어야 합니다.");
		}
		if (mealCount < 0) {
			throw new IllegalArgumentException("급식 수는 음수일 수 없습니다.");
		}
	}
}
