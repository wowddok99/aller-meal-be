package com.allermeal.application.meal;

import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.school.SchoolId;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record PersonalizedMealQueryResult(
	ChildProfileId childProfileId,
	SchoolId schoolId,
	LocalDate rangeStart,
	LocalDate rangeEnd,
	PublicMealCollectionStatus collectionStatus,
	Integer retryAfterSeconds,
	List<PersonalizedMealRiskResult> meals,
	List<PublicMealTarget> pendingTargets
) {

	public PersonalizedMealQueryResult {
		Objects.requireNonNull(childProfileId, "자녀 ID는 null일 수 없습니다.");
		Objects.requireNonNull(schoolId, "학교 ID는 null일 수 없습니다.");
		Objects.requireNonNull(rangeStart, "조회 시작일은 null일 수 없습니다.");
		Objects.requireNonNull(rangeEnd, "조회 종료일은 null일 수 없습니다.");
		Objects.requireNonNull(collectionStatus, "수집 상태는 null일 수 없습니다.");
		meals = List.copyOf(Objects.requireNonNull(meals, "급식 위험도 목록은 null일 수 없습니다."));
		pendingTargets = List.copyOf(Objects.requireNonNull(pendingTargets, "대기 중인 수집 대상은 null일 수 없습니다."));
	}
}
