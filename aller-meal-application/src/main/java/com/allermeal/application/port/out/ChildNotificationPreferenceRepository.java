package com.allermeal.application.port.out;

import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.child.NotificationPreference;
import com.allermeal.domain.user.UserId;
import java.util.Optional;

public interface ChildNotificationPreferenceRepository {

	Optional<NotificationPreference> findByChildProfileIdAndOwnerId(ChildProfileId childProfileId, UserId ownerId);
	Optional<NotificationPreference> upsert(UserId ownerId, NotificationPreference notificationPreference);
}
