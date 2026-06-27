package com.allermeal.application.notification;

public record ScheduledNotificationTargetGenerationResult(
	int duePreferenceCount,
	int createdTargetCount,
	int duplicateTargetCount
) {

	public ScheduledNotificationTargetGenerationResult {
		if (duePreferenceCount < 0 || createdTargetCount < 0 || duplicateTargetCount < 0) {
			throw new IllegalArgumentException("알림 대상 생성 결과 count는 음수일 수 없습니다.");
		}
	}
}
