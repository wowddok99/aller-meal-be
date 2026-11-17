package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminNotificationRequestItemResult;
import com.allermeal.domain.notification.NotificationChannel;
import com.allermeal.domain.notification.NotificationReason;
import com.allermeal.domain.notification.NotificationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminNotificationRequestItemResponse(
	UUID notificationId, UUID notificationTargetId, UUID childProfileId, UUID ownerId, LocalDate notificationDate,
	NotificationChannel channel, NotificationReason reason, NotificationStatus status, int attemptCount, int maxAttempts,
	Instant nextAttemptAt, Instant sentAt, String failureCode, String failureMessage, Instant createdAt, Instant updatedAt
) {
	public static AdminNotificationRequestItemResponse from(AdminNotificationRequestItemResult result) {
		return new AdminNotificationRequestItemResponse(result.notificationId().value(), result.notificationTargetId(),
			result.childProfileId().value(), result.ownerId().value(), result.notificationDate(), result.channel(), result.reason(),
			result.status(), result.attemptCount(), result.maxAttempts(), result.nextAttemptAt(), result.sentAt(),
			result.failureCode(), safeFailureMessage(result.failureCode()), result.createdAt(), result.updatedAt());
	}

	private static String safeFailureMessage(String code) {
		if (code == null) return null;
		return switch (code) {
			case "SMTP_SEND_FAILED" -> "알림 메일 발송에 실패했습니다.";
			case "NOTIFICATION_PREPARATION_FAILED" -> "알림 내용 준비에 실패했습니다.";
			case "SUPERSEDED_BY_CORRECTION" -> "정정 알림으로 대체되었습니다.";
			case "PERSONAL_DATA_MASKED", "OWNER_ACCESS_RESTRICTED", "NOTIFICATION_CONTEXT_CHANGED" -> null;
			default -> "알림 처리에 실패했습니다.";
		};
	}
}
