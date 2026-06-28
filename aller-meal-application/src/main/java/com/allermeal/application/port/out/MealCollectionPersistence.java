package com.allermeal.application.port.out;

import com.allermeal.application.port.out.result.MealCollectionCompletionResult;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.raw.RawObjectMetadata;
import java.util.List;

public interface MealCollectionPersistence {

	MealCollectionCompletionResult complete(
		CollectionJob runningJob,
		CollectionJob succeededJob,
		RawObjectMetadata rawObject,
		List<Meal> meals
	);
}
