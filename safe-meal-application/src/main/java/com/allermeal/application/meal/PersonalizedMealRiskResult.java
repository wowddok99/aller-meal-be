package com.allermeal.application.meal;

import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.risk.ChildMealRisk;
import java.util.Objects;

public record PersonalizedMealRiskResult(Meal meal, ChildMealRisk risk) {

	public PersonalizedMealRiskResult {
		Objects.requireNonNull(meal, "급식은 null일 수 없습니다.");
		Objects.requireNonNull(risk, "급식 위험도는 null일 수 없습니다.");
	}
}
