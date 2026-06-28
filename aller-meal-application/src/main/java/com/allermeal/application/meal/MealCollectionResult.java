package com.allermeal.application.meal;

import com.allermeal.application.port.out.result.MealSaveResult;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.raw.RawObjectMetadata;
import java.util.List;

public record MealCollectionResult(
	CollectionJob collectionJob,
	RawObjectMetadata rawObject,
	List<MealSaveResult> meals
) {

	public MealCollectionResult {
		meals = List.copyOf(meals);
	}
}
