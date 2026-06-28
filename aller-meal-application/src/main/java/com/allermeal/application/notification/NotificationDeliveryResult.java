package com.allermeal.application.notification;

import com.allermeal.domain.notification.NotificationId;
import com.allermeal.domain.notification.NotificationStatus;
import java.util.Objects;

public record NotificationDeliveryResult(NotificationId notificationId, NotificationStatus status, int attemptCount) {

	public NotificationDeliveryResult {
		Objects.requireNonNull(notificationId, "알림 ID는 null일 수 없습니다.");
		Objects.requireNonNull(status, "알림 상태는 null일 수 없습니다.");
		if (attemptCount < 0) {
			throw new IllegalArgumentException("알림 발송 시도 횟수는 음수일 수 없습니다.");
		}
	}
}
