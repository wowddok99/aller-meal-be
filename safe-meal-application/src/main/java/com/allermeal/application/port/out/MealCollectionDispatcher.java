package com.allermeal.application.port.out;

import com.allermeal.domain.collection.CollectionJob;

public interface MealCollectionDispatcher {

	void dispatch(CollectionJob collectionJob);
}
