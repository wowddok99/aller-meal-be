package com.allermeal.batch;

import com.allermeal.application.meal.MealCollectionEntrypoint;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "safe-meal.collection.run-once", name = "enabled", havingValue = "true")
public class MealCollectionApplicationRunner implements ApplicationRunner {

	private final MealCollectionEntrypoint entrypoint;
	private final SchoolId schoolId;
	private final LocalDate mealDate;
	private final MealType mealType;

	public MealCollectionApplicationRunner(
		MealCollectionEntrypoint entrypoint,
		@Value("${safe-meal.collection.run-once.school-id}") UUID schoolId,
		@Value("${safe-meal.collection.run-once.meal-date}") LocalDate mealDate,
		@Value("${safe-meal.collection.run-once.meal-type}") MealType mealType
	) {
		this.entrypoint = entrypoint;
		this.schoolId = new SchoolId(schoolId);
		this.mealDate = mealDate;
		this.mealType = mealType;
	}

	@Override
	public void run(ApplicationArguments args) {
		entrypoint.collect(schoolId, mealDate, mealType);
	}
}
