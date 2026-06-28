package com.allermeal.application.notification;

import com.allermeal.application.port.out.ChildAllergenRepository;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.NotificationTargetRepository;
import com.allermeal.application.port.out.command.NotificationTargetCommand;
import com.allermeal.application.port.out.result.DueNotificationPreferenceResult;
import com.allermeal.application.port.out.result.MealQueryResult;
import com.allermeal.application.port.out.result.NotificationTargetSaveResult;
import com.allermeal.domain.child.NotificationPreference;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.meal.MealId;
import com.allermeal.domain.meal.MealItemId;
import com.allermeal.domain.risk.ChildMealRisk;
import com.allermeal.domain.risk.ChildMealRiskCalculator;
import com.allermeal.domain.risk.MealRiskLevel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class NotificationTargetScheduler {

	private static final ZoneId DEFAULT_ZONE = ZoneId.of(NotificationPreference.SUPPORTED_TIMEZONE);

	private final NotificationTargetRepository notificationTargetRepository;
	private final ChildAllergenRepository childAllergenRepository;
	private final MealRepository mealRepository;
	private final Clock clock;

	public NotificationTargetScheduler(
		NotificationTargetRepository notificationTargetRepository,
		ChildAllergenRepository childAllergenRepository,
		MealRepository mealRepository,
		Clock clock
	) {
		this.notificationTargetRepository = notificationTargetRepository;
		this.childAllergenRepository = childAllergenRepository;
		this.mealRepository = mealRepository;
		this.clock = clock;
	}

	public ScheduledNotificationTargetGenerationResult generateDue(Duration lookback) {
		Objects.requireNonNull(lookback, "알림 대상 조회 lookback은 null일 수 없습니다.");
		if (lookback.isNegative() || lookback.compareTo(Duration.ofHours(23)) > 0) {
			throw new IllegalArgumentException("알림 대상 조회 lookback은 0 이상 23시간 이하여야 합니다.");
		}
		LocalTime now = LocalTime.now(clock.withZone(DEFAULT_ZONE)).truncatedTo(ChronoUnit.MINUTES);
		LocalTime windowStart = now.minus(lookback).truncatedTo(ChronoUnit.MINUTES);
		if (windowStart.isAfter(now)) {
			windowStart = LocalTime.MIDNIGHT;
		}
		return generate(LocalDate.now(clock.withZone(DEFAULT_ZONE)), windowStart, now);
	}

	public ScheduledNotificationTargetGenerationResult generate(
		LocalDate notificationDate,
		LocalTime windowStartInclusive,
		LocalTime windowEndInclusive
	) {
		Objects.requireNonNull(notificationDate, "알림 대상 날짜는 null일 수 없습니다.");
		Objects.requireNonNull(windowStartInclusive, "알림 대상 시작 시각은 null일 수 없습니다.");
		Objects.requireNonNull(windowEndInclusive, "알림 대상 종료 시각은 null일 수 없습니다.");
		if (windowStartInclusive.isAfter(windowEndInclusive)) {
			throw new IllegalArgumentException("알림 대상 시작 시각은 종료 시각보다 늦을 수 없습니다.");
		}
		List<DueNotificationPreferenceResult> duePreferences = notificationTargetRepository.findDuePreferences(
			notificationDate, windowStartInclusive, windowEndInclusive);
		List<NotificationTargetCommand> targets = duePreferences.stream()
			.map(preference -> createTarget(preference, notificationDate))
			.toList();
		NotificationTargetSaveResult saveResult = notificationTargetRepository.saveAll(targets);
		return new ScheduledNotificationTargetGenerationResult(
			duePreferences.size(), saveResult.createdCount(), saveResult.duplicateCount());
	}

	private NotificationTargetCommand createTarget(DueNotificationPreferenceResult preference, LocalDate notificationDate) {
		List<MealQueryResult> collectionResults = mealRepository.findCollectedInRange(
			preference.schoolId(), notificationDate, notificationDate);
		List<Meal> meals = collectionResults.stream()
			.map(MealQueryResult::meal)
			.filter(Objects::nonNull)
			.sorted(Comparator.comparing(Meal::mealType))
			.toList();
		TargetRisk targetRisk = targetRisk(preference, collectionResults, meals);
		return new NotificationTargetCommand(
			UUID.randomUUID(),
			preference.childProfileId(),
			preference.ownerId(),
			preference.schoolId(),
			notificationDate,
			preference.notificationTime(),
			NotificationPreference.SUPPORTED_TIMEZONE,
			targetRisk.reason(),
			targetRisk.riskLevel(),
			targetRisk.riskVersion(),
			meals.size(),
			clock.instant());
	}

	private TargetRisk targetRisk(
		DueNotificationPreferenceResult preference,
		List<MealQueryResult> collectionResults,
		List<Meal> meals
	) {
		if (collectionResults.isEmpty()) {
			return new TargetRisk(NotificationTargetReason.RISK_PENDING, MealRiskLevel.PENDING, null);
		}
		if (meals.isEmpty()) {
			return new TargetRisk(NotificationTargetReason.NO_MEAL, null, null);
		}
		List<Integer> childAllergens = childAllergenRepository.findAllergenCodes(
			preference.ownerId(), preference.childProfileId());
		Map<MealItemId, List<Integer>> allergenCodes = mealRepository.findAllergenCodesByMealIds(meals.stream()
			.map(Meal::id)
			.toList());
		List<ChildMealRisk> risks = new ArrayList<>();
		for (Meal meal : meals) {
			risks.add(ChildMealRiskCalculator.calculate(
				preference.childProfileId(), childAllergens, meal, allergenCodesForMeal(meal, allergenCodes)));
		}
		MealRiskLevel aggregate = aggregate(risks);
		return new TargetRisk(reason(aggregate), aggregate, aggregateRiskVersion(risks));
	}

	private Map<MealItemId, List<Integer>> allergenCodesForMeal(Meal meal, Map<MealItemId, List<Integer>> allergenCodes) {
		return meal.items().stream()
			.collect(java.util.stream.Collectors.toMap(
				item -> item.id(),
				item -> allergenCodes.getOrDefault(item.id(), List.of())));
	}

	private MealRiskLevel aggregate(List<ChildMealRisk> risks) {
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

	private NotificationTargetReason reason(MealRiskLevel riskLevel) {
		return switch (riskLevel) {
			case RISKY -> NotificationTargetReason.RISK_DETECTED;
			case SAFE -> NotificationTargetReason.NO_RISK;
			case UNKNOWN -> NotificationTargetReason.RISK_UNKNOWN;
			case LABELING_FAILED -> NotificationTargetReason.RISK_LABELING_FAILED;
			case PENDING -> NotificationTargetReason.RISK_PENDING;
		};
	}

	private String aggregateRiskVersion(List<ChildMealRisk> risks) {
		String canonical = risks.stream()
			.sorted(Comparator.comparing(risk -> risk.mealId().value()))
			.map(risk -> risk.mealId().value() + ":" + risk.riskVersion() + ":" + risk.riskLevel())
			.collect(java.util.stream.Collectors.joining("\n"));
		return sha256Hex(canonical);
	}

	private String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
		}
	}

	private record TargetRisk(NotificationTargetReason reason, MealRiskLevel riskLevel, String riskVersion) {
	}
}
