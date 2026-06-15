package com.allermeal.application.meal;

import com.allermeal.domain.meal.MealType;
import java.time.LocalDate;

public record PublicMealTarget(LocalDate mealDate, MealType mealType) {
}
