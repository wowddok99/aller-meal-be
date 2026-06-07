package com.allermeal.infra.consumer;

import com.allermeal.application.consumer.IdempotentEventConsumer;
import com.allermeal.application.consumer.IncomingEvent;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "safe-meal.consumer.enabled", havingValue = "true")
public class WorkerEventListener {

	private static final Logger log = LoggerFactory.getLogger(WorkerEventListener.class);
	private static final String CONSUMER_NAME = "safe-meal-worker";

	private final IdempotentEventConsumer consumer;
	private final RabbitMqRetryRouter retryRouter;
	private final TransactionTemplate transactionTemplate;

	public WorkerEventListener(
		IdempotentEventConsumer consumer,
		RabbitMqRetryRouter retryRouter,
		TransactionTemplate transactionTemplate
	) {
		this.consumer = consumer;
		this.retryRouter = retryRouter;
		this.transactionTemplate = transactionTemplate;
	}

	@RabbitListener(queues = "${safe-meal.rabbitmq.events.queue}")
	public void listen(Message message) {
		retryRouter.process(message, () -> transactionTemplate.executeWithoutResult(status -> consume(message)));
	}

	private void consume(Message message) {
		String messageId = message.getMessageProperties().getMessageId();
		String eventType = message.getMessageProperties().getType();
		IncomingEvent event = new IncomingEvent(
			UUID.fromString(messageId),
			eventType,
			new String(message.getBody(), StandardCharsets.UTF_8));
		consumer.consume(CONSUMER_NAME, event,
			incoming -> log.info("이벤트를 처리했습니다. eventId={}, eventType={}", incoming.id(), incoming.type()));
	}
}
