package com.allermeal.application.port.out.result;

import com.allermeal.domain.notification.NotificationRequest;
import java.util.Objects;

public record NotificationRequestSaveResult(
	boolean created,
	boolean correction,
	int canceledSupersededCount,
	NotificationRequest notificationRequest
) {

	public NotificationRequestSaveResult {
		if (canceledSupersededCount < 0) {
			throw new IllegalArgumentException("대체 취소된 알림 수는 음수일 수 없습니다.");
		}
		if (created) {
			Objects.requireNonNull(notificationRequest, "생성된 알림 요청은 null일 수 없습니다.");
		}
	}

	public static NotificationRequestSaveResult duplicate() {
		return new NotificationRequestSaveResult(false, false, 0, null);
	}
}
