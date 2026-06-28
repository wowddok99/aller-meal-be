package com.allermeal.application.port.out.result;

import com.allermeal.domain.school.SchoolId;
import java.time.Instant;
import java.util.Objects;

public record ActiveSchoolCollectionSubscriptionResult(
	SchoolId schoolId,
	int registeredChildCount,
	Instant updatedAt
) {

	public ActiveSchoolCollectionSubscriptionResult {
		Objects.requireNonNull(schoolId, "학교 ID는 null일 수 없습니다.");
		if (registeredChildCount <= 0) {
			throw new IllegalArgumentException("활성 구독의 등록 자녀 수는 1 이상이어야 합니다.");
		}
		Objects.requireNonNull(updatedAt, "구독 갱신 시각은 null일 수 없습니다.");
	}
}
