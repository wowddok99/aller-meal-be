package com.allermeal.application.child;

import com.allermeal.application.port.out.ChildNotificationPreferenceRepository;
import com.allermeal.application.port.out.ChildProfileRepository;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.child.NotificationPreference;
import com.allermeal.domain.common.EntityTimestamps;
import com.allermeal.domain.user.UserId;
import java.time.Clock;

public final class ChildNotificationPreferenceService {

	private final ChildProfileRepository childProfileRepository;
	private final ChildNotificationPreferenceRepository notificationPreferenceRepository;
	private final Clock clock;

	public ChildNotificationPreferenceService(
		ChildProfileRepository childProfileRepository,
		ChildNotificationPreferenceRepository notificationPreferenceRepository,
		Clock clock
	) {
		this.childProfileRepository = childProfileRepository;
		this.notificationPreferenceRepository = notificationPreferenceRepository;
		this.clock = clock;
	}

	public NotificationPreference find(UserId ownerId, ChildProfileId childProfileId) {
		return notificationPreferenceRepository.findByChildProfileIdAndOwnerId(childProfileId, ownerId)
			.orElseThrow(ChildProfileNotFoundException::new);
	}

	public NotificationPreference update(UserId ownerId, ChildProfileId childProfileId,
		UpdateChildNotificationPreferenceCommand command) {
		if (childProfileRepository.findByIdAndOwnerId(childProfileId, ownerId).isEmpty()) {
			throw new ChildProfileNotFoundException();
		}
		try {
			return notificationPreferenceRepository.upsert(ownerId, NotificationPreference.create(childProfileId,
				command.emailEnabled(), command.notificationTime(), command.timezone(), EntityTimestamps.createdAt(clock.instant())))
				.orElseThrow(ChildProfileNotFoundException::new);
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new InvalidChildNotificationPreferenceRequestException();
		}
	}
}
