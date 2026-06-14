package com.allermeal.application.port.out;

import com.allermeal.application.port.out.result.MealSaveResult;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.time.LocalDate;
import java.util.Optional;

public interface MealRepository {

	MealSaveResult save(Meal meal);

	Optional<Meal> findByNaturalKey(SchoolId schoolId, LocalDate mealDate, MealType mealType);
}
