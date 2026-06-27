package com.allermeal.application.notification;

import com.allermeal.domain.notification.NotificationRequest;

public final class NotificationRequestEvents {

	public static final String NOTIFICATION_REQUESTED = "NotificationRequested";

	private NotificationRequestEvents() {
	}

	public static String notificationRequestedPayload(NotificationRequest request) {
		return """
			{"notificationId":"%s","notificationTargetId":"%s","childId":"%s","userId":"%s","notificationDate":"%s","channel":"%s","reason":"%s","dedupKey":"%s","correctionKey":"%s","contentVersion":"%s","correction":%s}
			""".formatted(
			request.id().value(),
			request.notificationTargetId(),
			request.childProfileId().value(),
			request.ownerId().value(),
			request.notificationDate(),
			request.channel(),
			request.reason(),
			request.dedupKey(),
			request.correctionKey(),
			request.contentVersion(),
			request.correction()).trim();
	}
}
