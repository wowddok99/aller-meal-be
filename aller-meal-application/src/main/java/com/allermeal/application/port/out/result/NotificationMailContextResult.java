package com.allermeal.application.port.out.result;

import com.allermeal.domain.school.SchoolId;

public record NotificationMailContextResult(
	PendingNotificationTargetResult target,
	SchoolId schoolId,
	String childName,
	String schoolName
) {
}
