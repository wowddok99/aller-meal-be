package com.allermeal.infra.config;

import com.allermeal.application.admin.AdminCollectionFailureService;
import com.allermeal.application.admin.AdminBootstrapProperties;
import com.allermeal.application.admin.AdminBootstrapService;
import com.allermeal.application.admin.AdminNotificationFailureService;
import com.allermeal.application.admin.AdminUserService;
import com.allermeal.application.consumer.IdempotentEventConsumer;
import com.allermeal.application.child.ChildProfileService;
import com.allermeal.application.child.ChildAllergenService;
import com.allermeal.application.child.ChildNotificationPreferenceService;
import com.allermeal.application.meal.MealAllergenLabelingService;
import com.allermeal.application.meal.NeisAllergenLabelParser;
import com.allermeal.application.notification.NotificationDeliveryService;
import com.allermeal.application.notification.NotificationHistoryService;
import com.allermeal.application.notification.NotificationRequestCreationService;
import com.allermeal.application.meal.PersonalizedMealQueryService;
import com.allermeal.application.meal.PublicMealQueryService;
import com.allermeal.application.port.out.AdminAuditLogRepository;
import com.allermeal.application.port.out.AdminBootstrapLockRepository;
import com.allermeal.application.port.out.AdminNotificationReprocessRequestRepository;
import com.allermeal.application.port.out.AdminRecollectionRequestRepository;
import com.allermeal.application.port.out.AllergenRepository;
import com.allermeal.application.port.out.ChildAllergenRepository;
import com.allermeal.application.outbox.OutboxPublisher;
import com.allermeal.application.port.out.ConsumedEventRepository;
import com.allermeal.application.port.out.ChildProfileRepository;
import com.allermeal.application.port.out.ChildNotificationPreferenceRepository;
import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.DeadLetterEventRepository;
import com.allermeal.application.port.out.EventPublisher;
import com.allermeal.application.port.out.EmailDecryptor;
import com.allermeal.application.port.out.EmailEncryptor;
import com.allermeal.application.port.out.EmailSearchHasher;
import com.allermeal.application.port.out.ExternalApiLogRepository;
import com.allermeal.application.port.out.MealCollectionDispatcher;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.NotificationMailSender;
import com.allermeal.application.port.out.NotificationRequestRepository;
import com.allermeal.application.port.out.OutboxEventRepository;
import com.allermeal.application.port.out.PasswordHasher;
import com.allermeal.application.port.out.PublicMealQueryCache;
import com.allermeal.application.port.out.SchoolRepository;
import com.allermeal.application.port.out.SchoolCollectionSubscriptionRepository;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.infra.consumer.RabbitMqRetryRouter;
import com.allermeal.infra.notification.SmtpNotificationMailSender;
import com.allermeal.infra.outbox.RabbitMqEventPublisher;
import java.time.Clock;
import java.time.Duration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EntityScan(basePackages = "com.allermeal.infra.user")
@EnableJpaRepositories(basePackages = "com.allermeal.infra.user")
public class AllerMealRuntimeConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	@Bean
	AdminBootstrapProperties adminBootstrapProperties(
		@Value("${aller-meal.admin.bootstrap.email:}") String email,
		@Value("${aller-meal.admin.bootstrap.password:}") String password
	) {
		return new AdminBootstrapProperties(email, password);
	}

	@Bean
	AdminBootstrapService adminBootstrapService(
		UserRepository userRepository,
		AdminBootstrapLockRepository bootstrapLockRepository,
		EmailEncryptor emailEncryptor,
		EmailSearchHasher emailSearchHasher,
		PasswordHasher passwordHasher,
		AdminAuditLogRepository auditLogRepository,
		AdminBootstrapProperties properties,
		Clock clock
	) {
		return new AdminBootstrapService(
			userRepository, bootstrapLockRepository, emailEncryptor, emailSearchHasher, passwordHasher,
			auditLogRepository, properties, clock);
	}

	@Bean
	AdminUserService adminUserService(
		UserRepository userRepository,
		AdminAuditLogRepository auditLogRepository,
		Clock clock
	) {
		return new AdminUserService(userRepository, auditLogRepository, clock);
	}

	@Bean
	AdminCollectionFailureService adminCollectionFailureService(
		CollectionJobRepository collectionJobRepository,
		ExternalApiLogRepository externalApiLogRepository,
		AdminRecollectionRequestRepository recollectionRequestRepository,
		MealCollectionDispatcher collectionDispatcher,
		AdminAuditLogRepository auditLogRepository,
		Clock clock
	) {
		return new AdminCollectionFailureService(
			collectionJobRepository, externalApiLogRepository, recollectionRequestRepository,
			collectionDispatcher, auditLogRepository, clock);
	}

	@Bean
	AdminNotificationFailureService adminNotificationFailureService(
		NotificationRequestRepository notificationRequestRepository,
		DeadLetterEventRepository deadLetterEventRepository,
		AdminNotificationReprocessRequestRepository reprocessRequestRepository,
		OutboxEventRepository outboxEventRepository,
		AdminAuditLogRepository auditLogRepository,
		Clock clock
	) {
		return new AdminNotificationFailureService(
			notificationRequestRepository, deadLetterEventRepository, reprocessRequestRepository,
			outboxEventRepository, auditLogRepository, clock);
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
	NotificationRequestCreationService notificationRequestCreationService(
		NotificationRequestRepository notificationRequestRepository,
		OutboxEventRepository outboxEventRepository,
		Clock clock
	) {
		return new NotificationRequestCreationService(notificationRequestRepository, outboxEventRepository, clock);
	}

	@Bean
	NotificationHistoryService notificationHistoryService(
		ChildProfileRepository childProfileRepository,
		NotificationRequestRepository notificationRequestRepository
	) {
		return new NotificationHistoryService(childProfileRepository, notificationRequestRepository);
	}

	@Bean
	NotificationMailSender notificationMailSender(
		JavaMailSender mailSender,
		@Value("${aller-meal.notification.email-from:no-reply@allermeal.local}") String from
	) {
		return new SmtpNotificationMailSender(mailSender, from);
	}

	@Bean
	NotificationDeliveryService notificationDeliveryService(
		NotificationRequestRepository notificationRequestRepository,
		UserRepository userRepository,
		EmailDecryptor emailDecryptor,
		NotificationMailSender notificationMailSender,
		Clock clock,
		@Value("${aller-meal.notification.retry-delay:5m}") Duration retryDelay
	) {
		return new NotificationDeliveryService(
			notificationRequestRepository, userRepository, emailDecryptor, notificationMailSender, clock, retryDelay);
	}

	@Bean
	IdempotentEventConsumer idempotentEventConsumer(ConsumedEventRepository repository, Clock clock) {
		return new IdempotentEventConsumer(repository, clock);
	}

	@Bean
	EventPublisher eventPublisher(
		RabbitTemplate rabbitTemplate,
		@Value("${aller-meal.rabbitmq.events.exchange}") String exchange,
		@Value("${aller-meal.rabbitmq.events.routing-key}") String routingKey,
		@Value("${aller-meal.rabbitmq.publisher-confirm-timeout}") Duration confirmTimeout
	) {
		rabbitTemplate.setMandatory(true);
		return new RabbitMqEventPublisher(rabbitTemplate, exchange, routingKey, confirmTimeout);
	}

	@Bean
	RabbitMqRetryRouter rabbitMqRetryRouter(
		RabbitTemplate rabbitTemplate,
		DeadLetterEventRepository deadLetterEventRepository,
		Clock clock,
		@Value("${aller-meal.rabbitmq.retry.exchange}") String retryExchange,
		@Value("${aller-meal.rabbitmq.retry.routing-key}") String retryRoutingKey,
		@Value("${aller-meal.rabbitmq.dead-letter.exchange}") String deadLetterExchange,
		@Value("${aller-meal.rabbitmq.dead-letter.routing-key}") String deadLetterRoutingKey,
		@Value("${aller-meal.rabbitmq.max-retries}") int maxRetries,
		@Value("${aller-meal.rabbitmq.publisher-confirm-timeout}") Duration confirmTimeout
	) {
		return new RabbitMqRetryRouter(
			rabbitTemplate, retryExchange, retryRoutingKey, deadLetterExchange, deadLetterRoutingKey, maxRetries,
			confirmTimeout, deadLetterEventRepository, clock);
	}
}
