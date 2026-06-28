package com.allermeal.domain.meal;

import java.util.Objects;

public record MealItem(
	MealItemId id,
	String name,
	String rawText,
	int displayOrder,
	MealItemLabelingStatus labelingStatus
) {

	private static final int MAX_NAME_LENGTH = 300;
	private static final int MAX_RAW_TEXT_LENGTH = 1000;

	public MealItem {
		Objects.requireNonNull(id, "메뉴 ID는 null일 수 없습니다.");
		name = requireNormalizedText(name, MAX_NAME_LENGTH, "메뉴 이름");
		rawText = requireRawText(rawText);
		if (displayOrder < 0) {
			throw new IllegalArgumentException("메뉴 표시 순서는 0 이상이어야 합니다.");
		}
		Objects.requireNonNull(labelingStatus, "메뉴 라벨링 상태는 null일 수 없습니다.");
	}

	private static String requireNormalizedText(String value, int maxLength, String fieldName) {
		Objects.requireNonNull(value, fieldName + "은 필수입니다.");
		String normalized = value.trim().replaceAll("\\s+", " ");
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(fieldName + "은 필수입니다.");
		}
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(fieldName + "은 " + maxLength + "자를 초과할 수 없습니다.");
		}
		return normalized;
	}

	private static String requireRawText(String value) {
		Objects.requireNonNull(value, "메뉴 원본 텍스트는 필수입니다.");
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("메뉴 원본 텍스트는 필수입니다.");
		}
		if (trimmed.length() > MAX_RAW_TEXT_LENGTH) {
			throw new IllegalArgumentException("메뉴 원본 텍스트는 1000자를 초과할 수 없습니다.");
		}
		return trimmed;
	}
}
