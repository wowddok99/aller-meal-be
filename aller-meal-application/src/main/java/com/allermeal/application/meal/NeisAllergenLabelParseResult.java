package com.allermeal.application.meal;

import java.util.List;
import java.util.Objects;

public record NeisAllergenLabelParseResult(
	NeisAllergenLabelParseStatus status,
	List<Integer> allergenCodes
) {

	public NeisAllergenLabelParseResult {
		Objects.requireNonNull(status, "라벨링 파싱 상태는 null일 수 없습니다.");
		allergenCodes = List.copyOf(allergenCodes);
	}

	public static NeisAllergenLabelParseResult labeled(List<Integer> allergenCodes) {
		if (allergenCodes == null || allergenCodes.isEmpty()) {
			throw new IllegalArgumentException("라벨링된 알레르기 번호는 비어 있을 수 없습니다.");
		}
		return new NeisAllergenLabelParseResult(NeisAllergenLabelParseStatus.LABELED, allergenCodes);
	}

	public static NeisAllergenLabelParseResult unknown() {
		return new NeisAllergenLabelParseResult(NeisAllergenLabelParseStatus.UNKNOWN, List.of());
	}

	public static NeisAllergenLabelParseResult failed() {
		return new NeisAllergenLabelParseResult(NeisAllergenLabelParseStatus.LABELING_FAILED, List.of());
	}
}
