package com.allermeal.domain.risk;

import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.meal.MealId;
import java.util.List;
import java.util.Objects;

public record ChildMealRisk(
	ChildProfileId childProfileId,
	MealId mealId,
	String riskVersion,
	MealRiskLevel riskLevel,
	List<MealItemRisk> itemRisks
) {

	public ChildMealRisk {
		Objects.requireNonNull(childProfileId, "자녀 ID는 null일 수 없습니다.");
		Objects.requireNonNull(mealId, "급식 ID는 null일 수 없습니다.");
		riskVersion = requireRiskVersion(riskVersion);
		Objects.requireNonNull(riskLevel, "급식 위험도는 null일 수 없습니다.");
		itemRisks = List.copyOf(Objects.requireNonNull(itemRisks, "메뉴 위험도 목록은 null일 수 없습니다."));
		if (itemRisks.isEmpty()) {
			throw new IllegalArgumentException("급식 위험도에는 메뉴 위험도 목록이 필요합니다.");
		}
	}

	private static String requireRiskVersion(String value) {
		Objects.requireNonNull(value, "위험도 version은 null일 수 없습니다.");
		String normalized = value.trim();
		if (!normalized.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("위험도 version은 SHA-256 hex 형식이어야 합니다.");
		}
		return normalized;
	}
}
