package com.allermeal.application.port.out;

import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.raw.RawObjectMetadata;
import java.util.List;

public interface MealCollectionPersistence {

	CompletionResult complete(
		CollectionJob runningJob,
		CollectionJob succeededJob,
		RawObjectMetadata rawObject,
		List<Meal> meals
	);

	record CompletionResult(CollectionJob collectionJob, List<MealRepository.MealSaveResult> meals) {

		public CompletionResult {
			meals = List.copyOf(meals);
		}
	}
}
