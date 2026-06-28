package com.allermeal.application.port.out;

import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.raw.RawObjectMetadata;
import com.allermeal.domain.school.School;
import java.time.LocalDate;
import java.util.List;

public interface NeisMealNormalizer {

	List<Meal> normalize(
		School school,
		LocalDate mealDate,
		MealType mealType,
		byte[] rawPayload,
		RawObjectMetadata metadata
	);
}
