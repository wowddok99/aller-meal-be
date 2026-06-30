package com.allermeal.application.port.out;

import com.allermeal.application.port.out.result.NotificationRequestSaveResult;
import com.allermeal.application.port.out.result.PendingNotificationTargetResult;
import com.allermeal.application.admin.AdminFailedNotificationPageResult;
import com.allermeal.application.notification.NotificationHistoryResult;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.notification.NotificationRequest;
import com.allermeal.domain.notification.NotificationId;
import com.allermeal.domain.notification.NotificationStatus;
import com.allermeal.domain.user.UserId;
import java.util.List;
import java.util.Optional;

public interface NotificationRequestRepository {

	List<PendingNotificationTargetResult> findPendingTargets(int limit);

	NotificationRequestSaveResult saveCorrectionAware(NotificationRequest request);

	Optional<NotificationRequest> findById(NotificationId notificationId);

	Optional<NotificationRequest> startSendingIfOwnerActive(
		NotificationStatus expectedStatus,
		NotificationRequest request
	);

	NotificationRequest save(NotificationStatus expectedStatus, NotificationRequest request);

	NotificationHistoryResult findHistoryByChild(UserId ownerId, ChildProfileId childProfileId, int page, int pageSize);

	AdminFailedNotificationPageResult findFailed(int page, int pageSize);
}
