package com.allermeal.infra.consumer;

import com.allermeal.application.port.out.DeadLetterEventRepository;
import com.allermeal.application.port.out.command.DeadLetterEventCommand;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMqRetryRouter {

	public static final String RETRY_COUNT_HEADER = "x-aller-meal-retry-count";
	private static final Logger log = LoggerFactory.getLogger(RabbitMqRetryRouter.class);

	private final RabbitTemplate rabbitTemplate;
	private final String retryExchange;
	private final String retryRoutingKey;
	private final String deadLetterExchange;
	private final String deadLetterRoutingKey;
	private final int maxRetries;
	private final Duration confirmTimeout;
	private final DeadLetterEventRepository deadLetterEventRepository;
	private final Clock clock;

	public RabbitMqRetryRouter(
		RabbitTemplate rabbitTemplate,
		String retryExchange,
		String retryRoutingKey,
		String deadLetterExchange,
		String deadLetterRoutingKey,
		int maxRetries,
		Duration confirmTimeout,
		DeadLetterEventRepository deadLetterEventRepository,
		Clock clock
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
		this.deadLetterEventRepository = deadLetterEventRepository;
		this.clock = clock;
	}

	public void process(Message message, Runnable processing) {
		try {
			processing.run();
		} catch (RuntimeException exception) {
			if (exception instanceof DeadLetterRoutingException) {
				sendDeadLetter(message);
				return;
			}
			int retryCount = retryCount(message);
			if (retryCount >= maxRetries) {
				sendDeadLetter(message);
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

	private void sendDeadLetter(Message message) {
		sendConfirmed(deadLetterExchange, deadLetterRoutingKey, message);
		try {
			deadLetterEventRepository.save(new DeadLetterEventCommand(
				UUID.randomUUID(),
				message.getMessageProperties().getMessageId(),
				message.getMessageProperties().getType(),
				new String(message.getBody(), StandardCharsets.UTF_8),
				retryCount(message),
				clock.instant()));
		} catch (RuntimeException exception) {
			log.error("DLQ 이벤트 snapshot 저장에 실패했습니다. messageId={}, eventType={}",
				message.getMessageProperties().getMessageId(),
				message.getMessageProperties().getType(),
				exception);
		}
	}

	private int retryCount(Message message) {
		Object value = message.getMessageProperties().getHeaders().get(RETRY_COUNT_HEADER);
		return value instanceof Number number ? number.intValue() : 0;
	}
}
