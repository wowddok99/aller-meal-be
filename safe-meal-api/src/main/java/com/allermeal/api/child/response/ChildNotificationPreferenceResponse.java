package com.allermeal.api.child.response;

import com.allermeal.domain.child.NotificationPreference;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record ChildNotificationPreferenceResponse(UUID childId, boolean emailEnabled, LocalTime notificationTime,
	String timezone, Instant createdAt, Instant updatedAt) {

	public static ChildNotificationPreferenceResponse from(NotificationPreference notificationPreference) {
		return new ChildNotificationPreferenceResponse(notificationPreference.childProfileId().value(),
			notificationPreference.emailEnabled(), notificationPreference.notificationTime(), notificationPreference.timezone(),
			notificationPreference.timestamps().createdAt(), notificationPreference.timestamps().updatedAt());
	}
}
