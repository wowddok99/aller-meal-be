package com.allermeal.infra.config;

import com.allermeal.application.consumer.IdempotentEventConsumer;
import com.allermeal.application.outbox.OutboxPublisher;
import com.allermeal.application.port.out.ConsumedEventRepository;
import com.allermeal.application.port.out.EventPublisher;
import com.allermeal.application.port.out.OutboxEventRepository;
import com.allermeal.infra.consumer.RabbitMqRetryRouter;
import com.allermeal.infra.outbox.RabbitMqEventPublisher;
import java.time.Clock;
import java.time.Duration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SafeMealRuntimeConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
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
