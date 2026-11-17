package com.allermeal.application.port.out.command;

import com.allermeal.domain.meal.MealType;
import java.util.List;
import java.util.Objects;

public record NotificationMealItemCommand(MealType mealType, String menuName, List<String> matchedAllergens) {
	public NotificationMealItemCommand {
		Objects.requireNonNull(mealType, "식사 구분은 null일 수 없습니다.");
		Objects.requireNonNull(menuName, "메뉴 이름은 null일 수 없습니다.");
		matchedAllergens = List.copyOf(matchedAllergens);
	}
}
