package com.allermeal.application.port.out.result;

public record NotificationTargetSaveResult(int createdCount, int duplicateCount) {

	public NotificationTargetSaveResult {
		if (createdCount < 0 || duplicateCount < 0) {
			throw new IllegalArgumentException("알림 대상 저장 결과 count는 음수일 수 없습니다.");
		}
	}
}
