package com.allermeal.application.port.out;

import com.allermeal.application.port.out.result.MealSaveResult;
import com.allermeal.application.port.out.result.MealQueryResult;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.meal.MealId;
import com.allermeal.domain.meal.MealItemId;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MealRepository {

	MealSaveResult save(Meal meal);

	Optional<Meal> findById(MealId mealId);

	Optional<Meal> findByNaturalKey(SchoolId schoolId, LocalDate mealDate, MealType mealType);

	List<MealQueryResult> findCollectedInRange(SchoolId schoolId, LocalDate startDate, LocalDate endDate);

	boolean saveAllergenLabels(Meal meal, Map<MealItemId, List<Integer>> allergenCodesByItemId);
}
