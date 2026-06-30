package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.AdminNotificationReprocessRequestCommand;
import com.allermeal.application.port.out.result.AdminNotificationReprocessRequestResult;
import java.util.Optional;
import java.util.UUID;

public interface AdminNotificationReprocessRequestRepository {

	AdminNotificationReprocessRequestResult save(AdminNotificationReprocessRequestCommand command);

	Optional<AdminNotificationReprocessRequestResult> findByIdempotencyKey(
		String idempotencyKey,
		UUID deadLetterEventId
	);
}
