package com.allermeal.domain.meal;

import com.allermeal.domain.school.SchoolId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record Meal(
	MealId id,
	SchoolId schoolId,
	LocalDate mealDate,
	MealType mealType,
	String sourceVersion,
	Instant sourceReceivedAt,
	MealLabelingStatus labelingStatus,
	String nutritionInfo,
	String originInfo,
	List<MealItem> items
) {

	private static final int MAX_SOURCE_VERSION_LENGTH = 200;
	private static final int MAX_METADATA_LENGTH = 20_000;

	public Meal {
		Objects.requireNonNull(id, "급식 ID는 null일 수 없습니다.");
		Objects.requireNonNull(schoolId, "학교 ID는 null일 수 없습니다.");
		Objects.requireNonNull(mealDate, "급식 날짜는 null일 수 없습니다.");
		Objects.requireNonNull(mealType, "식사 타입은 null일 수 없습니다.");
		sourceVersion = requireText(sourceVersion, MAX_SOURCE_VERSION_LENGTH, "급식 source version");
		Objects.requireNonNull(sourceReceivedAt, "급식 원본 수신 시각은 null일 수 없습니다.");
		Objects.requireNonNull(labelingStatus, "급식 라벨링 상태는 null일 수 없습니다.");
		nutritionInfo = normalizeNullable(nutritionInfo, "영양 정보");
		originInfo = normalizeNullable(originInfo, "원산지 정보");
		items = List.copyOf(Objects.requireNonNull(items, "메뉴 목록은 null일 수 없습니다."));
		if (items.isEmpty()) {
			throw new IllegalArgumentException("급식에는 메뉴가 하나 이상 필요합니다.");
		}
		if (items.stream().map(MealItem::displayOrder).distinct().count() != items.size()) {
			throw new IllegalArgumentException("메뉴 표시 순서는 급식 내에서 중복될 수 없습니다.");
		}
		if (new HashSet<>(items.stream().map(MealItem::id).toList()).size() != items.size()) {
			throw new IllegalArgumentException("메뉴 ID는 급식 내에서 중복될 수 없습니다.");
		}
		validateLabelingStatus(labelingStatus, items);
	}

	private static void validateLabelingStatus(MealLabelingStatus status, List<MealItem> items) {
		boolean hasPending = items.stream().anyMatch(item -> item.labelingStatus() == MealItemLabelingStatus.PENDING);
		boolean hasUnknown = items.stream().anyMatch(item -> item.labelingStatus() == MealItemLabelingStatus.UNKNOWN);
		boolean hasFailed = items.stream().anyMatch(item -> item.labelingStatus() == MealItemLabelingStatus.LABELING_FAILED);
		boolean valid = switch (status) {
			case PENDING -> items.stream().allMatch(item -> item.labelingStatus() == MealItemLabelingStatus.PENDING);
			case LABELED -> items.stream().allMatch(item -> item.labelingStatus() == MealItemLabelingStatus.LABELED);
			case UNKNOWN -> !hasPending && !hasFailed && hasUnknown;
			case LABELING_FAILED -> !hasPending && hasFailed;
		};
		if (!valid) {
			throw new IllegalArgumentException("급식과 메뉴 라벨링 상태가 일치하지 않습니다.");
		}
	}

	private static String requireText(String value, int maxLength, String fieldName) {
		String normalized = normalizeNullable(value, fieldName);
		if (normalized == null) {
			throw new IllegalArgumentException(fieldName + "은 필수입니다.");
		}
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(fieldName + "은 " + maxLength + "자를 초과할 수 없습니다.");
		}
		return normalized;
	}

	private static String normalizeNullable(String value, String fieldName) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		if (normalized.isEmpty()) {
			return null;
		}
		if (normalized.length() > MAX_METADATA_LENGTH) {
			throw new IllegalArgumentException(fieldName + "는 20000자를 초과할 수 없습니다.");
		}
		return normalized;
	}
}
