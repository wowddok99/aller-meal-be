package com.allermeal.domain.risk;

import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.meal.MealItem;
import com.allermeal.domain.meal.MealItemId;
import com.allermeal.domain.meal.MealItemLabelingStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class ChildMealRiskCalculator {

	private ChildMealRiskCalculator() {
	}

	public static ChildMealRisk calculate(
		ChildProfileId childProfileId,
		List<Integer> childAllergenCodes,
		Meal meal,
		Map<MealItemId, List<Integer>> allergenCodesByItemId
	) {
		Objects.requireNonNull(childProfileId, "자녀 ID는 null일 수 없습니다.");
		Objects.requireNonNull(meal, "급식은 null일 수 없습니다.");
		Objects.requireNonNull(allergenCodesByItemId, "메뉴별 알레르기 코드는 null일 수 없습니다.");
		Set<Integer> childAllergens = normalizedCodes(childAllergenCodes, "자녀 알레르기 코드");

		List<MealItemRisk> itemRisks = new ArrayList<>();
		for (MealItem item : meal.items()) {
			List<Integer> itemAllergens = normalizedCodes(
				allergenCodesByItemId.getOrDefault(item.id(), List.of()),
				"메뉴 알레르기 코드").stream().toList();
			itemRisks.add(calculateItemRisk(item, childAllergens, itemAllergens));
		}

		return new ChildMealRisk(
			childProfileId,
			meal.id(),
			riskVersion(childProfileId, childAllergens, meal, itemRisks, allergenCodesByItemId),
			aggregate(itemRisks),
			itemRisks);
	}

	private static MealItemRisk calculateItemRisk(
		MealItem item,
		Set<Integer> childAllergens,
		List<Integer> itemAllergens
	) {
		if (item.labelingStatus() != MealItemLabelingStatus.LABELED) {
			return new MealItemRisk(item.id(), item.labelingStatus(), unsafeLevel(item.labelingStatus()), List.of());
		}
		List<Integer> matched = itemAllergens.stream()
			.filter(childAllergens::contains)
			.sorted()
			.toList();
		return new MealItemRisk(
			item.id(),
			item.labelingStatus(),
			matched.isEmpty() ? MealRiskLevel.SAFE : MealRiskLevel.RISKY,
			matched);
	}

	private static MealRiskLevel unsafeLevel(MealItemLabelingStatus status) {
		return switch (status) {
			case UNKNOWN -> MealRiskLevel.UNKNOWN;
			case LABELING_FAILED -> MealRiskLevel.LABELING_FAILED;
			case PENDING -> MealRiskLevel.PENDING;
			case LABELED -> throw new IllegalArgumentException("라벨링 완료 메뉴는 별도 계산해야 합니다.");
		};
	}

	private static MealRiskLevel aggregate(List<MealItemRisk> itemRisks) {
		if (itemRisks.stream().anyMatch(item -> item.riskLevel() == MealRiskLevel.RISKY)) {
			return MealRiskLevel.RISKY;
		}
		if (itemRisks.stream().anyMatch(item -> item.riskLevel() == MealRiskLevel.LABELING_FAILED)) {
			return MealRiskLevel.LABELING_FAILED;
		}
		if (itemRisks.stream().anyMatch(item -> item.riskLevel() == MealRiskLevel.UNKNOWN)) {
			return MealRiskLevel.UNKNOWN;
		}
		if (itemRisks.stream().anyMatch(item -> item.riskLevel() == MealRiskLevel.PENDING)) {
			return MealRiskLevel.PENDING;
		}
		return MealRiskLevel.SAFE;
	}

	private static Set<Integer> normalizedCodes(List<Integer> values, String fieldName) {
		Objects.requireNonNull(values, fieldName + "는 null일 수 없습니다.");
		Set<Integer> normalized = new TreeSet<>();
		for (Integer value : values) {
			if (value == null || value <= 0) {
				throw new IllegalArgumentException(fieldName + "는 양수여야 합니다.");
			}
			normalized.add(value);
		}
		return normalized;
	}

	private static String riskVersion(
		ChildProfileId childProfileId,
		Set<Integer> childAllergens,
		Meal meal,
		List<MealItemRisk> itemRisks,
		Map<MealItemId, List<Integer>> allergenCodesByItemId
	) {
		Set<MealItemId> seenItemIds = new HashSet<>();
		StringBuilder canonical = new StringBuilder()
			.append("child=").append(childProfileId.value()).append('\n')
			.append("childAllergens=").append(childAllergens).append('\n')
			.append("meal=").append(meal.id().value()).append('\n')
			.append("mealDate=").append(meal.mealDate()).append('\n')
			.append("mealType=").append(meal.mealType()).append('\n')
			.append("sourceVersion=").append(meal.sourceVersion()).append('\n')
			.append("sourceReceivedAt=").append(meal.sourceReceivedAt()).append('\n')
			.append("mealLabelingStatus=").append(meal.labelingStatus()).append('\n');
		for (MealItem item : meal.items()) {
			MealItemRisk itemRisk = itemRisks.stream()
				.filter(candidate -> candidate.mealItemId().equals(item.id()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("메뉴 위험도 계산 결과가 누락되었습니다."));
			seenItemIds.add(item.id());
			List<Integer> itemAllergens = normalizedCodes(
				allergenCodesByItemId.getOrDefault(item.id(), List.of()),
				"메뉴 알레르기 코드").stream().toList();
			canonical.append("item=").append(item.id().value())
				.append(",displayOrder=").append(item.displayOrder())
				.append(",name=").append(item.name())
				.append(",rawText=").append(item.rawText())
				.append(",status=").append(itemRisk.labelingStatus())
				.append(",allergens=").append(itemAllergens)
				.append('\n');
		}
		if (!seenItemIds.containsAll(allergenCodesByItemId.keySet())) {
			throw new IllegalArgumentException("급식에 없는 메뉴의 알레르기 코드가 포함되었습니다.");
		}
		return sha256Hex(canonical.toString());
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
		}
	}
}
