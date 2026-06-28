package com.allermeal.application.port.out.command;

import com.allermeal.domain.notification.NotificationId;
import java.util.Objects;

public record NotificationMailCommand(
	NotificationId notificationId,
	String recipientEmail,
	String subject,
	String body
) {

	public NotificationMailCommand {
		Objects.requireNonNull(notificationId, "알림 ID는 null일 수 없습니다.");
		recipientEmail = requireText(recipientEmail, "수신자 이메일");
		subject = requireText(subject, "알림 메일 제목");
		body = requireText(body, "알림 메일 본문");
	}

	private static String requireText(String value, String fieldName) {
		Objects.requireNonNull(value, fieldName + "은 null일 수 없습니다.");
		String normalized = value.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(fieldName + "은 비어 있을 수 없습니다.");
		}
		return normalized;
	}
}
