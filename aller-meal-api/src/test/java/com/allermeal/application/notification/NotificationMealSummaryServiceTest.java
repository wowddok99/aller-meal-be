package com.allermeal.application.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.allermeal.application.port.out.*;
import com.allermeal.application.port.out.result.*;
import com.allermeal.domain.allergen.Allergen;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.meal.*;
import com.allermeal.domain.notification.NotificationReason;
import com.allermeal.domain.risk.*;
import com.allermeal.domain.school.SchoolId;
import com.allermeal.domain.user.UserId;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

final class NotificationMealSummaryServiceTest {
	private final ChildProfileId child = new ChildProfileId(UUID.randomUUID());
	private final UserId owner = new UserId(UUID.randomUUID());
	private final SchoolId school = new SchoolId(UUID.randomUUID());
	private final LocalDate date = LocalDate.of(2026, 11, 13);
	private final MealItem item = new MealItem(new MealItemId(UUID.randomUUID()), "우유", "우유(2)", 0, MealItemLabelingStatus.LABELED);
	private final Meal meal = new Meal(new MealId(UUID.randomUUID()), school, date, MealType.LUNCH, "original",
		Instant.parse("2026-11-13T00:00:00Z"), MealLabelingStatus.LABELED, null, null, List.of(item));
	private final MealRepository meals = mock(MealRepository.class);
	private final ChildAllergenRepository childAllergens = mock(ChildAllergenRepository.class);
	private final AllergenRepository allergens = mock(AllergenRepository.class);
	private final NotificationMealSummaryService service = new NotificationMealSummaryService(meals, childAllergens, allergens);

	@BeforeEach
	void prepare() {
		when(meals.findCollectedInRange(school, date, date)).thenReturn(List.of(new MealQueryResult(date, MealType.LUNCH, meal)));
		when(meals.findAllergenCodesByMealIds(List.of(meal.id()))).thenReturn(Map.of(item.id(), List.of(2)));
		when(childAllergens.findAllergenCodes(owner, child)).thenReturn(List.of(2));
		when(allergens.findAll()).thenReturn(List.of(new Allergen(2, "우유")));
	}

	@Test
	void summarizesOnlyRealMenusAndMatchingAllergensWhenHistoricalVersionMatches() {
		var result = service.summarize(context());
		assertEquals(1, result.size());
		assertEquals("우유", result.getFirst().menuName());
		assertEquals(MealType.LUNCH, result.getFirst().mealType());
		assertEquals(List.of("우유"), result.getFirst().matchedAllergens());
	}

	@Test
	void changedChildAllergensOrMenuContentOrMissingMealsFallsBackWithoutInventingDetails() {
		var context = context();
		when(childAllergens.findAllergenCodes(owner, child)).thenReturn(List.of(1));
		assertTrue(service.summarize(context).isEmpty());
		when(childAllergens.findAllergenCodes(owner, child)).thenReturn(List.of(2));
		var changed = new Meal(meal.id(), school, date, MealType.LUNCH, "new-source", meal.sourceReceivedAt(),
			meal.labelingStatus(), null, null, List.of(item));
		when(meals.findCollectedInRange(school, date, date)).thenReturn(List.of(new MealQueryResult(date, MealType.LUNCH, changed)));
		assertTrue(service.summarize(context).isEmpty());
		when(meals.findCollectedInRange(school, date, date)).thenReturn(List.of());
		assertTrue(service.summarize(context).isEmpty());
	}

	@Test
	void missingAllergenDisplayNameOrDifferentHistoricalReasonCannotProduceMisleadingSummary() {
		var context = context();
		when(allergens.findAll()).thenReturn(List.of());
		assertTrue(service.summarize(context).isEmpty());
		var target = context.target();
		var differentReason = new PendingNotificationTargetResult(target.notificationTargetId(), child, owner, date,
			NotificationReason.NO_RISK, MealRiskLevel.SAFE, target.riskVersion(), 1);
		assertTrue(service.summarize(new NotificationMailContextResult(differentReason, school, "자녀", "학교")).isEmpty());
	}

	@Test
	void schedulerAndDeliveryUseTheSameHistoricalRiskFingerprint() {
		NotificationTargetRepository targets = mock(NotificationTargetRepository.class);
		when(targets.findDuePreferences(eq(date), any(), any())).thenReturn(List.of(
			new DueNotificationPreferenceResult(child, owner, school, LocalTime.of(8, 0))));
		when(targets.saveAll(any())).thenReturn(new NotificationTargetSaveResult(1, 0));
		new NotificationTargetScheduler(targets, childAllergens, meals, Clock.systemUTC()).generate(date, LocalTime.MIN, LocalTime.MAX);
		verify(targets).saveAll(argThat(values -> values.size() == 1
			&& values.getFirst().riskVersion().equals(context().target().riskVersion())
			&& values.getFirst().reason() == NotificationTargetReason.RISK_DETECTED));
	}

	private NotificationMailContextResult context() {
		var risk = ChildMealRiskCalculator.calculate(child, List.of(2), meal, Map.of(item.id(), List.of(2)));
		var target = new PendingNotificationTargetResult(UUID.randomUUID(), child, owner, date, NotificationReason.RISK_DETECTED,
			MealRiskLevel.RISKY, NotificationRiskSummary.aggregateRiskVersion(List.of(risk)), 1);
		return new NotificationMailContextResult(target, school, "자녀", "학교");
	}
}
