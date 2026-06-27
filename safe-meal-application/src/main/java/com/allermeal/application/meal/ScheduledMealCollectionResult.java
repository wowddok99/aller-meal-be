package com.allermeal.application.meal;

public record ScheduledMealCollectionResult(
	int activeSubscriptionCount,
	int targetCount,
	int requestedJobCount,
	int skippedJobCount
) {

	public ScheduledMealCollectionResult {
		if (activeSubscriptionCount < 0 || targetCount < 0 || requestedJobCount < 0 || skippedJobCount < 0) {
			throw new IllegalArgumentException("수집 Scheduler 결과 count는 0 이상이어야 합니다.");
		}
	}
}
