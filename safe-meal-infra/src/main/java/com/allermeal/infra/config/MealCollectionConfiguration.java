package com.allermeal.infra.config;

import com.allermeal.application.meal.MealCollectionService;
import com.allermeal.application.meal.MealCollectionEntrypoint;
import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.MealCollectionPersistence;
import com.allermeal.application.port.out.NeisMealClient;
import com.allermeal.application.port.out.NeisMealNormalizer;
import com.allermeal.application.port.out.RawPayloadStorage;
import com.allermeal.application.port.out.SchoolRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
		@Value("${safe-meal.neis.meal.lease-duration:2m}") Duration leaseDuration
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
}
