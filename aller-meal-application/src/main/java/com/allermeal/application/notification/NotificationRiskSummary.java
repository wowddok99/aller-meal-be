package com.allermeal.application.notification;

import com.allermeal.domain.risk.ChildMealRisk;
import com.allermeal.domain.risk.MealRiskLevel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

final class NotificationRiskSummary {

	private NotificationRiskSummary() { }

	static MealRiskLevel aggregate(List<ChildMealRisk> risks) {
		if (risks.stream().anyMatch(risk -> risk.riskLevel() == MealRiskLevel.RISKY)) {
			return MealRiskLevel.RISKY;
		}
		if (risks.stream().anyMatch(risk -> risk.riskLevel() == MealRiskLevel.LABELING_FAILED)) {
			return MealRiskLevel.LABELING_FAILED;
		}
		if (risks.stream().anyMatch(risk -> risk.riskLevel() == MealRiskLevel.UNKNOWN)) {
			return MealRiskLevel.UNKNOWN;
		}
		if (risks.stream().anyMatch(risk -> risk.riskLevel() == MealRiskLevel.PENDING)) {
			return MealRiskLevel.PENDING;
		}
		return MealRiskLevel.SAFE;
	}

	static NotificationTargetReason reason(MealRiskLevel riskLevel) {
		return switch (riskLevel) {
			case RISKY -> NotificationTargetReason.RISK_DETECTED;
			case SAFE -> NotificationTargetReason.NO_RISK;
			case UNKNOWN -> NotificationTargetReason.RISK_UNKNOWN;
			case LABELING_FAILED -> NotificationTargetReason.RISK_LABELING_FAILED;
			case PENDING -> NotificationTargetReason.RISK_PENDING;
		};
	}

	static String aggregateRiskVersion(List<ChildMealRisk> risks) {
		String canonical = risks.stream()
			.sorted(Comparator.comparing(risk -> risk.mealId().value()))
			.map(risk -> risk.mealId().value() + ":" + risk.riskVersion() + ":" + risk.riskLevel())
			.collect(java.util.stream.Collectors.joining("\n"));
		return sha256Hex(canonical);
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
