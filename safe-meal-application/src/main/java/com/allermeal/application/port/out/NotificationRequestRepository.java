package com.allermeal.application.port.out;

import com.allermeal.application.port.out.result.NotificationRequestSaveResult;
import com.allermeal.application.port.out.result.PendingNotificationTargetResult;
import com.allermeal.domain.notification.NotificationRequest;
import com.allermeal.domain.notification.NotificationId;
import com.allermeal.domain.notification.NotificationStatus;
import java.util.List;
import java.util.Optional;

public interface NotificationRequestRepository {

	List<PendingNotificationTargetResult> findPendingTargets(int limit);

	NotificationRequestSaveResult saveCorrectionAware(NotificationRequest request);

	Optional<NotificationRequest> findById(NotificationId notificationId);

	NotificationRequest save(NotificationStatus expectedStatus, NotificationRequest request);
}
