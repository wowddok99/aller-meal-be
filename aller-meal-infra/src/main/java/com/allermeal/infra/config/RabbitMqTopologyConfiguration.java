package com.allermeal.infra.config;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqTopologyConfiguration {

	@Bean
	DirectExchange eventsExchange(@Value("${aller-meal.rabbitmq.events.exchange}") String name) {
		return new DirectExchange(name, true, false);
	}

	@Bean
	Queue eventsQueue(@Value("${aller-meal.rabbitmq.events.queue}") String name) {
		return new Queue(name, true);
	}

	@Bean
	Binding eventsBinding(
		@Qualifier("eventsQueue") Queue eventsQueue,
		@Qualifier("eventsExchange") DirectExchange eventsExchange,
		@Value("${aller-meal.rabbitmq.events.routing-key}") String routingKey
	) {
		return BindingBuilder.bind(eventsQueue).to(eventsExchange).with(routingKey);
	}

	@Bean
	DirectExchange retryExchange(@Value("${aller-meal.rabbitmq.retry.exchange}") String name) {
		return new DirectExchange(name, true, false);
	}

	@Bean
	Queue retryQueue(
		@Value("${aller-meal.rabbitmq.retry.queue}") String name,
		@Value("${aller-meal.rabbitmq.retry.delay}") long delay,
		@Value("${aller-meal.rabbitmq.events.exchange}") String eventsExchange,
		@Value("${aller-meal.rabbitmq.events.routing-key}") String eventsRoutingKey
	) {
		return new Queue(name, true, false, false, Map.of(
			"x-message-ttl", delay,
			"x-dead-letter-exchange", eventsExchange,
			"x-dead-letter-routing-key", eventsRoutingKey));
	}

	@Bean
	Binding retryBinding(
		@Qualifier("retryQueue") Queue retryQueue,
		@Qualifier("retryExchange") DirectExchange retryExchange,
		@Value("${aller-meal.rabbitmq.retry.routing-key}") String routingKey
	) {
		return BindingBuilder.bind(retryQueue).to(retryExchange).with(routingKey);
	}

	@Bean
	DirectExchange deadLetterExchange(@Value("${aller-meal.rabbitmq.dead-letter.exchange}") String name) {
		return new DirectExchange(name, true, false);
	}

	@Bean
	Queue deadLetterQueue(@Value("${aller-meal.rabbitmq.dead-letter.queue}") String name) {
		return new Queue(name, true);
	}

	@Bean
	Binding deadLetterBinding(
		@Qualifier("deadLetterQueue") Queue deadLetterQueue,
		@Qualifier("deadLetterExchange") DirectExchange deadLetterExchange,
		@Value("${aller-meal.rabbitmq.dead-letter.routing-key}") String routingKey
	) {
		return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(routingKey);
	}
}
