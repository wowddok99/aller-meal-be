package com.allermeal.domain.child;

import com.allermeal.domain.common.EntityTimestamps;
import java.time.LocalTime;
import java.util.Objects;

public final class NotificationPreference {

	public static final String SUPPORTED_TIMEZONE = "Asia/Seoul";

	private final ChildProfileId childProfileId;
	private final boolean emailEnabled;
	private final LocalTime notificationTime;
	private final String timezone;
	private final EntityTimestamps timestamps;

	private NotificationPreference(ChildProfileId childProfileId, boolean emailEnabled, LocalTime notificationTime,
		String timezone, EntityTimestamps timestamps) {
		this.childProfileId = Objects.requireNonNull(childProfileId, "자녀 ID는 null일 수 없습니다.");
		this.emailEnabled = emailEnabled;
		this.notificationTime = Objects.requireNonNull(notificationTime, "알림 시각은 null일 수 없습니다.");
		this.timezone = requireSupportedTimezone(timezone);
		this.timestamps = Objects.requireNonNull(timestamps, "알림 설정 시각 정보는 null일 수 없습니다.");
	}

	public static NotificationPreference create(ChildProfileId childProfileId, boolean emailEnabled,
		LocalTime notificationTime, String timezone, EntityTimestamps timestamps) {
		return new NotificationPreference(childProfileId, emailEnabled, notificationTime, timezone, timestamps);
	}

	private static String requireSupportedTimezone(String value) {
		if (!SUPPORTED_TIMEZONE.equals(value)) throw new IllegalArgumentException("지원하지 않는 시간대입니다.");
		return value;
	}

	public ChildProfileId childProfileId() { return childProfileId; }
	public boolean emailEnabled() { return emailEnabled; }
	public LocalTime notificationTime() { return notificationTime; }
	public String timezone() { return timezone; }
	public EntityTimestamps timestamps() { return timestamps; }
}
