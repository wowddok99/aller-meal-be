package com.allermeal.infra.meal;

import com.allermeal.application.meal.MealCollectionService;
import com.allermeal.application.port.out.ConcurrentStateChangeException;
import com.allermeal.application.port.out.MealCollectionDispatcher;
import com.allermeal.domain.collection.CollectionJob;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AsyncMealCollectionDispatcher implements MealCollectionDispatcher {

	private static final Logger log = LoggerFactory.getLogger(AsyncMealCollectionDispatcher.class);

	private final Executor executor;
	private final MealCollectionService collectionService;

	public AsyncMealCollectionDispatcher(Executor executor, MealCollectionService collectionService) {
		this.executor = executor;
		this.collectionService = collectionService;
	}

	@Override
	public void dispatch(CollectionJob collectionJob) {
		try {
			executor.execute(() -> collect(collectionJob));
		} catch (RuntimeException exception) {
			log.error("공개 급식 비동기 수집 실행 요청에 실패했습니다. collectionJobId={}",
				collectionJob.id().value(), exception);
		}
	}

	private void collect(CollectionJob collectionJob) {
		try {
			collectionService.collect(collectionJob);
		} catch (ConcurrentStateChangeException ignored) {
			log.debug("공개 급식 수집 작업이 이미 다른 실행자에 의해 처리 중입니다. collectionJobId={}",
				collectionJob.id().value());
		} catch (RuntimeException exception) {
			log.error("공개 급식 비동기 수집에 실패했습니다. collectionJobId={}",
				collectionJob.id().value(), exception);
		}
	}
}
