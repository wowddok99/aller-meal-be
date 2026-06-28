package com.allermeal.application.port.out.result;

import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.school.SchoolId;
import com.allermeal.domain.user.UserId;
import java.time.LocalTime;
import java.util.Objects;

public record DueNotificationPreferenceResult(
	ChildProfileId childProfileId,
	UserId ownerId,
	SchoolId schoolId,
	LocalTime notificationTime
) {

	public DueNotificationPreferenceResult {
		Objects.requireNonNull(childProfileId, "자녀 ID는 null일 수 없습니다.");
		Objects.requireNonNull(ownerId, "소유자 ID는 null일 수 없습니다.");
		Objects.requireNonNull(schoolId, "학교 ID는 null일 수 없습니다.");
		Objects.requireNonNull(notificationTime, "알림 시각은 null일 수 없습니다.");
	}
}
