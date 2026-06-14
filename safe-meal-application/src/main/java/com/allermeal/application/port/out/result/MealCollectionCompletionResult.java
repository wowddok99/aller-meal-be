package com.allermeal.application.port.out.result;

import com.allermeal.domain.collection.CollectionJob;
import java.util.List;

public record MealCollectionCompletionResult(CollectionJob collectionJob, List<MealSaveResult> meals) {

	public MealCollectionCompletionResult {
		meals = List.copyOf(meals);
	}
}
