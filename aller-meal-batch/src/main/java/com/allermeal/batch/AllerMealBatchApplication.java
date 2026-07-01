package com.allermeal.batch;

import com.allermeal.application.account.ExpiredAccountPersonalDataCleanupService;
import com.allermeal.application.outbox.OutboxPublisher;
import com.allermeal.application.notification.NotificationRequestCreationService;
import com.allermeal.application.port.out.AccountWithdrawalPrivacyRepository;
import com.allermeal.application.port.out.EventPublisher;
import com.allermeal.application.port.out.NotificationRequestRepository;
import com.allermeal.application.port.out.OperationLogRetentionRepository;
import com.allermeal.application.port.out.OutboxEventRepository;
import com.allermeal.application.port.out.RawPayloadObjectRemover;
import com.allermeal.application.port.out.RawPayloadRetentionRepository;
import com.allermeal.application.retention.DataRetentionCleanupService;
import com.allermeal.infra.account.JdbcAccountWithdrawalPrivacyRepository;
import com.allermeal.infra.admin.JdbcExternalApiLogRepository;
import com.allermeal.infra.collection.JdbcCollectionJobRepository;
import com.allermeal.infra.config.NotificationTargetConfiguration;
import com.allermeal.infra.config.MealCollectionConfiguration;
import com.allermeal.infra.config.MinioConfiguration;
import com.allermeal.infra.config.RabbitMqTopologyConfiguration;
import com.allermeal.infra.child.JdbcChildAllergenRepository;
import com.allermeal.infra.meal.NeisHttpMealClient;
import com.allermeal.infra.notification.JdbcNotificationTargetRepository;
import com.allermeal.infra.notification.JdbcNotificationRequestRepository;
import com.allermeal.infra.outbox.RabbitMqEventPublisher;
import com.allermeal.infra.outbox.JdbcOutboxEventRepository;
import com.allermeal.infra.raw.MinioRawPayloadStorage;
import com.allermeal.infra.retention.JdbcOperationLogRetentionRepository;
import com.allermeal.infra.school.JdbcSchoolRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

@EnableScheduling
@Import({
	MealCollectionConfiguration.class,
	MinioConfiguration.class,
	NotificationTargetConfiguration.class,
	RabbitMqTopologyConfiguration.class
})
@SpringBootApplication(scanBasePackageClasses = {
	AllerMealBatchApplication.class,
	JdbcAccountWithdrawalPrivacyRepository.class,
	JdbcExternalApiLogRepository.class,
	JdbcChildAllergenRepository.class,
	JdbcCollectionJobRepository.class,
	NeisHttpMealClient.class,
	JdbcNotificationTargetRepository.class,
	JdbcNotificationRequestRepository.class,
	JdbcOutboxEventRepository.class,
	MinioRawPayloadStorage.class,
	JdbcOperationLogRetentionRepository.class,
	JdbcSchoolRepository.class
})
public class AllerMealBatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(AllerMealBatchApplication.class, args);
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper();
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
	ExpiredAccountPersonalDataCleanupService expiredAccountPersonalDataCleanupService(
		AccountWithdrawalPrivacyRepository privacyRepository,
		Clock clock
	) {
		return new ExpiredAccountPersonalDataCleanupService(privacyRepository, clock);
	}

	@Bean
	DataRetentionCleanupService dataRetentionCleanupService(
		RawPayloadRetentionRepository rawPayloadRetentionRepository,
		RawPayloadObjectRemover rawPayloadObjectRemover,
		OperationLogRetentionRepository operationLogRetentionRepository,
		Clock clock,
		@Value("${aller-meal.retention.raw-payload.period:P90D}") Duration rawPayloadRetentionPeriod,
		@Value("${aller-meal.retention.operation-log.period:P365D}") Duration operationLogRetentionPeriod,
		@Value("${aller-meal.retention.raw-payload.batch-size:100}") int rawPayloadBatchSize,
		@Value("${aller-meal.retention.operation-log.batch-size:100}") int operationLogBatchSize
	) {
		return new DataRetentionCleanupService(
			rawPayloadRetentionRepository,
			rawPayloadObjectRemover,
			operationLogRetentionRepository,
			clock,
			rawPayloadRetentionPeriod,
			operationLogRetentionPeriod,
			rawPayloadBatchSize,
			operationLogBatchSize);
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
}
