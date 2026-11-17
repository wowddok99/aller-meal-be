package com.allermeal.application.port.out.command;

import com.allermeal.domain.notification.NotificationId;
import com.allermeal.domain.notification.NotificationReason;
import com.allermeal.domain.child.ChildProfileId;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record NotificationMailCommand(
	NotificationId notificationId,
	String recipientEmail,
	ChildProfileId childProfileId,
	String childName,
	String schoolName,
	LocalDate notificationDate,
	NotificationReason reason,
	List<NotificationMealItemCommand> items
) {

	public NotificationMailCommand {
		Objects.requireNonNull(notificationId, "알림 ID는 null일 수 없습니다.");
		recipientEmail = requireText(recipientEmail, "수신자 이메일");
		Objects.requireNonNull(childProfileId, "자녀 ID는 null일 수 없습니다.");
		childName = requireText(childName, "자녀 이름");
		schoolName = requireText(schoolName, "학교 이름");
		Objects.requireNonNull(notificationDate, "알림 날짜는 null일 수 없습니다.");
		Objects.requireNonNull(reason, "알림 사유는 null일 수 없습니다.");
		items = List.copyOf(items);
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
