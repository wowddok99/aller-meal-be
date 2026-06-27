package com.allermeal.application.notification;

public record NotificationRequestCreationResult(
	int targetCount,
	int createdCount,
	int duplicateCount,
	int correctionCount,
	int canceledSupersededCount
) {

	public NotificationRequestCreationResult {
		if (targetCount < 0 || createdCount < 0 || duplicateCount < 0 || correctionCount < 0
			|| canceledSupersededCount < 0) {
			throw new IllegalArgumentException("알림 요청 생성 결과 count는 음수일 수 없습니다.");
		}
	}
}
