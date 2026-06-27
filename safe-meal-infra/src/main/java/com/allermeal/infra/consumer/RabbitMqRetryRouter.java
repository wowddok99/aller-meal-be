package com.allermeal.infra.consumer;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class RabbitMqRetryRouter {

	public static final String RETRY_COUNT_HEADER = "x-safe-meal-retry-count";

	private final RabbitTemplate rabbitTemplate;
	private final String retryExchange;
	private final String retryRoutingKey;
	private final String deadLetterExchange;
	private final String deadLetterRoutingKey;
	private final int maxRetries;
	private final Duration confirmTimeout;

	public RabbitMqRetryRouter(
		RabbitTemplate rabbitTemplate,
		String retryExchange,
		String retryRoutingKey,
		String deadLetterExchange,
		String deadLetterRoutingKey,
		int maxRetries,
		Duration confirmTimeout
	) {
		if (maxRetries < 0) {
			throw new IllegalArgumentException("maxRetries는 음수일 수 없습니다.");
		}
		this.rabbitTemplate = rabbitTemplate;
		this.retryExchange = retryExchange;
		this.retryRoutingKey = retryRoutingKey;
		this.deadLetterExchange = deadLetterExchange;
		this.deadLetterRoutingKey = deadLetterRoutingKey;
		this.maxRetries = maxRetries;
		this.confirmTimeout = confirmTimeout;
	}

	public void process(Message message, Runnable processing) {
		try {
			processing.run();
		} catch (RuntimeException exception) {
			if (exception instanceof DeadLetterRoutingException) {
				sendConfirmed(deadLetterExchange, deadLetterRoutingKey, message);
				return;
			}
			int retryCount = retryCount(message);
			if (retryCount >= maxRetries) {
				sendConfirmed(deadLetterExchange, deadLetterRoutingKey, message);
				return;
			}
			Message retryMessage = MessageBuilder.fromMessage(message)
				.setHeader(RETRY_COUNT_HEADER, retryCount + 1)
				.build();
			sendConfirmed(retryExchange, retryRoutingKey, retryMessage);
		}
	}

	private void sendConfirmed(String exchange, String routingKey, Message message) {
		CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
		rabbitTemplate.send(exchange, routingKey, message, correlation);
		try {
			CorrelationData.Confirm confirm = correlation.getFuture()
				.get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
			if (!confirm.ack() || correlation.getReturned() != null) {
				throw new IllegalStateException("RabbitMQ가 retry/DLQ 전송을 확인하지 않았습니다.");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("RabbitMQ retry/DLQ 전송 확인 대기가 중단되었습니다.", exception);
		} catch (Exception exception) {
			throw new IllegalStateException("RabbitMQ retry/DLQ 전송 확인에 실패했습니다.", exception);
		}
	}

	private int retryCount(Message message) {
		Object value = message.getMessageProperties().getHeaders().get(RETRY_COUNT_HEADER);
		return value instanceof Number number ? number.intValue() : 0;
	}
}
