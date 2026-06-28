package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.NotificationTargetCommand;
import com.allermeal.application.port.out.result.DueNotificationPreferenceResult;
import com.allermeal.application.port.out.result.NotificationTargetSaveResult;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface NotificationTargetRepository {

	List<DueNotificationPreferenceResult> findDuePreferences(
		LocalDate notificationDate,
		LocalTime windowStartInclusive,
		LocalTime windowEndInclusive
	);

	NotificationTargetSaveResult saveAll(List<NotificationTargetCommand> commands);
}
