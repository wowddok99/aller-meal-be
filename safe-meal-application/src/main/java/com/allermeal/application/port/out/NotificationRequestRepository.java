package com.allermeal.application.port.out;

import com.allermeal.application.port.out.result.NotificationRequestSaveResult;
import com.allermeal.application.port.out.result.PendingNotificationTargetResult;
import com.allermeal.domain.notification.NotificationRequest;
import java.util.List;

public interface NotificationRequestRepository {

	List<PendingNotificationTargetResult> findPendingTargets(int limit);

	NotificationRequestSaveResult saveCorrectionAware(NotificationRequest request);
}
