package com.allermeal.application.port.out.result;

import java.util.UUID;

public record AdminNotificationReprocessRequestResult(
	UUID deadLetterEventId,
	boolean duplicate,
	boolean conflict
) {
}
