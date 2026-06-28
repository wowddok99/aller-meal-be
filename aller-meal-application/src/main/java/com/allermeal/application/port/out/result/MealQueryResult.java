package com.allermeal.application.port.out.result;

import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.meal.MealType;
import java.time.LocalDate;

public record MealQueryResult(LocalDate mealDate, MealType mealType, Meal meal) {
}
