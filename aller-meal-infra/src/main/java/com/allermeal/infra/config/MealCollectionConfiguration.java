package com.allermeal.infra.config;

import com.allermeal.application.meal.MealCollectionService;
import com.allermeal.application.meal.MealCollectionEntrypoint;
import com.allermeal.application.meal.RegisteredSchoolMealCollectionScheduler;
import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.MealCollectionDispatcher;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.PublicMealQueryCache;
import com.allermeal.application.port.out.MealCollectionPersistence;
import com.allermeal.application.port.out.NeisMealClient;
import com.allermeal.application.port.out.NeisMealNormalizer;
import com.allermeal.application.port.out.RawPayloadStorage;
import com.allermeal.application.port.out.SchoolRepository;
import com.allermeal.application.port.out.SchoolCollectionSubscriptionRepository;
import com.allermeal.application.meal.PublicMealQueryService;
import com.allermeal.infra.meal.AsyncMealCollectionDispatcher;
import com.allermeal.infra.meal.RedisPublicMealQueryCache;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class MealCollectionConfiguration {

	@Bean
	MealCollectionService mealCollectionService(
		SchoolRepository schoolRepository,
		CollectionJobRepository collectionJobRepository,
		NeisMealClient neisMealClient,
		RawPayloadStorage rawPayloadStorage,
		NeisMealNormalizer neisMealNormalizer,
		MealCollectionPersistence persistence,
		Clock clock,
		@Value("${aller-meal.neis.meal.lease-duration:2m}") Duration leaseDuration
	) {
		return new MealCollectionService(
			schoolRepository, collectionJobRepository, neisMealClient, rawPayloadStorage,
			neisMealNormalizer, persistence, clock, leaseDuration);
	}

	@Bean
	MealCollectionEntrypoint mealCollectionEntrypoint(
		CollectionJobRepository collectionJobRepository,
		MealCollectionService mealCollectionService,
		Clock clock
	) {
		return new MealCollectionEntrypoint(collectionJobRepository, mealCollectionService, clock);
	}

	@Bean
	RegisteredSchoolMealCollectionScheduler registeredSchoolMealCollectionScheduler(
		SchoolCollectionSubscriptionRepository subscriptionRepository,
		CollectionJobRepository collectionJobRepository,
		MealRepository mealRepository,
		MealCollectionDispatcher collectionDispatcher,
		Clock clock
	) {
		return new RegisteredSchoolMealCollectionScheduler(
			subscriptionRepository, collectionJobRepository, mealRepository, collectionDispatcher, clock);
	}

	@Bean
	TaskExecutor publicMealCollectionExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("public-meal-collection-");
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(100);
		return executor;
	}

	@Bean
	MealCollectionDispatcher mealCollectionDispatcher(
		@Qualifier("publicMealCollectionExecutor")
		TaskExecutor publicMealCollectionExecutor,
		MealCollectionService mealCollectionService
	) {
		return new AsyncMealCollectionDispatcher(publicMealCollectionExecutor, mealCollectionService);
	}

	@Bean
	PublicMealQueryService publicMealQueryService(
		SchoolRepository schoolRepository,
		MealRepository mealRepository,
		CollectionJobRepository collectionJobRepository,
		MealCollectionDispatcher collectionDispatcher,
		PublicMealQueryCache publicMealQueryCache,
		Clock clock
	) {
		return new PublicMealQueryService(
			schoolRepository, mealRepository, collectionJobRepository, collectionDispatcher,
			publicMealQueryCache, clock);
	}

	@Bean
	PublicMealQueryCache publicMealQueryCache(
		StringRedisTemplate redisTemplate,
		ObjectMapper objectMapper,
		@Value("${aller-meal.public-meal.cache-ttl:10m}") Duration cacheTtl,
		@Value("${aller-meal.public-meal.dispatch-lock-ttl:10s}") Duration dispatchLockTtl
	) {
		return new RedisPublicMealQueryCache(redisTemplate, objectMapper, cacheTtl, dispatchLockTtl);
	}
}
