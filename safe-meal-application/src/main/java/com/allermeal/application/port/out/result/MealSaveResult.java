package com.allermeal.application.port.out.result;

import com.allermeal.domain.meal.Meal;
import java.util.Objects;

public record MealSaveResult(Meal meal, boolean applied) {

	public MealSaveResult {
		Objects.requireNonNull(meal, "저장 결과 급식은 null일 수 없습니다.");
	}
}
