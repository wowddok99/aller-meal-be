package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminDeadLetterEventStatus;
import com.allermeal.application.admin.AdminNotificationReprocessResult;
import java.time.Instant;
import java.util.UUID;

public record AdminNotificationReprocessResponse(
	UUID deadLetterEventId,
	AdminDeadLetterEventStatus status,
	boolean duplicate,
	Instant reprocessedAt
) {

	public static AdminNotificationReprocessResponse from(AdminNotificationReprocessResult result) {
		return new AdminNotificationReprocessResponse(
			result.deadLetterEventId(),
			result.status(),
			result.duplicate(),
			result.reprocessedAt());
	}
}
