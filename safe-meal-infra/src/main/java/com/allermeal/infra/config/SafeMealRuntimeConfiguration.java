package com.allermeal.infra.config;

import com.allermeal.application.consumer.IdempotentEventConsumer;
import com.allermeal.application.child.ChildProfileService;
import com.allermeal.application.child.ChildAllergenService;
import com.allermeal.application.child.ChildNotificationPreferenceService;
import com.allermeal.application.meal.MealAllergenLabelingService;
import com.allermeal.application.meal.NeisAllergenLabelParser;
import com.allermeal.application.meal.PersonalizedMealQueryService;
import com.allermeal.application.meal.PublicMealQueryService;
import com.allermeal.application.port.out.AllergenRepository;
import com.allermeal.application.port.out.ChildAllergenRepository;
import com.allermeal.application.outbox.OutboxPublisher;
import com.allermeal.application.port.out.ConsumedEventRepository;
import com.allermeal.application.port.out.ChildProfileRepository;
import com.allermeal.application.port.out.ChildNotificationPreferenceRepository;
import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.EventPublisher;
import com.allermeal.application.port.out.MealCollectionDispatcher;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.OutboxEventRepository;
import com.allermeal.application.port.out.PublicMealQueryCache;
import com.allermeal.application.port.out.SchoolRepository;
import com.allermeal.application.port.out.SchoolCollectionSubscriptionRepository;
import com.allermeal.infra.consumer.RabbitMqRetryRouter;
import com.allermeal.infra.outbox.RabbitMqEventPublisher;
import java.time.Clock;
import java.time.Duration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EntityScan(basePackages = "com.allermeal.infra.user")
@EnableJpaRepositories(basePackages = "com.allermeal.infra.user")
public class SafeMealRuntimeConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	@Bean
	ChildProfileService childProfileService(
		ChildProfileRepository childProfileRepository,
		SchoolRepository schoolRepository,
		SchoolCollectionSubscriptionRepository subscriptionRepository,
		CollectionJobRepository collectionJobRepository,
		MealCollectionDispatcher collectionDispatcher,
		Clock clock
	) {
		return new ChildProfileService(
			childProfileRepository, schoolRepository, subscriptionRepository, collectionJobRepository, collectionDispatcher, clock);
	}

	@Bean
	ChildAllergenService childAllergenService(
		ChildProfileRepository childProfileRepository,
		ChildAllergenRepository childAllergenRepository,
		AllergenRepository allergenRepository
	) {
		return new ChildAllergenService(childProfileRepository, childAllergenRepository, allergenRepository);
	}

	@Bean
	ChildNotificationPreferenceService childNotificationPreferenceService(
		ChildProfileRepository childProfileRepository,
		ChildNotificationPreferenceRepository notificationPreferenceRepository,
		Clock clock
	) {
		return new ChildNotificationPreferenceService(childProfileRepository, notificationPreferenceRepository, clock);
	}

	@Bean
	PersonalizedMealQueryService personalizedMealQueryService(
		ChildProfileRepository childProfileRepository,
		ChildAllergenRepository childAllergenRepository,
		MealRepository mealRepository,
		PublicMealQueryService publicMealQueryService,
		Clock clock
	) {
		return new PersonalizedMealQueryService(
			childProfileRepository, childAllergenRepository, mealRepository, publicMealQueryService, clock);
	}

	@Bean
	NeisAllergenLabelParser neisAllergenLabelParser() {
		return new NeisAllergenLabelParser();
	}

	@Bean
	MealAllergenLabelingService mealAllergenLabelingService(
		MealRepository mealRepository,
		AllergenRepository allergenRepository,
		OutboxEventRepository outboxEventRepository,
		PublicMealQueryCache publicMealQueryCache,
		NeisAllergenLabelParser parser,
		Clock clock
	) {
		return new MealAllergenLabelingService(
			mealRepository, allergenRepository, outboxEventRepository, publicMealQueryCache, parser, clock);
	}

	@Bean
	OutboxPublisher outboxPublisher(OutboxEventRepository repository, EventPublisher eventPublisher, Clock clock) {
		return new OutboxPublisher(repository, eventPublisher, clock);
	}

	@Bean
	IdempotentEventConsumer idempotentEventConsumer(ConsumedEventRepository repository, Clock clock) {
		return new IdempotentEventConsumer(repository, clock);
	}

	@Bean
	EventPublisher eventPublisher(
		RabbitTemplate rabbitTemplate,
		@Value("${safe-meal.rabbitmq.events.exchange}") String exchange,
		@Value("${safe-meal.rabbitmq.events.routing-key}") String routingKey,
		@Value("${safe-meal.rabbitmq.publisher-confirm-timeout}") Duration confirmTimeout
	) {
		rabbitTemplate.setMandatory(true);
		return new RabbitMqEventPublisher(rabbitTemplate, exchange, routingKey, confirmTimeout);
	}

	@Bean
	RabbitMqRetryRouter rabbitMqRetryRouter(
		RabbitTemplate rabbitTemplate,
		@Value("${safe-meal.rabbitmq.retry.exchange}") String retryExchange,
		@Value("${safe-meal.rabbitmq.retry.routing-key}") String retryRoutingKey,
		@Value("${safe-meal.rabbitmq.dead-letter.exchange}") String deadLetterExchange,
		@Value("${safe-meal.rabbitmq.dead-letter.routing-key}") String deadLetterRoutingKey,
		@Value("${safe-meal.rabbitmq.max-retries}") int maxRetries,
		@Value("${safe-meal.rabbitmq.publisher-confirm-timeout}") Duration confirmTimeout
	) {
		return new RabbitMqRetryRouter(
			rabbitTemplate, retryExchange, retryRoutingKey, deadLetterExchange, deadLetterRoutingKey, maxRetries,
			confirmTimeout);
	}
}
