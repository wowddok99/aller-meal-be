package com.allermeal.infra.outbox;

import com.allermeal.application.port.out.EventPublisher;
import com.allermeal.domain.outbox.OutboxEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class RabbitMqEventPublisher implements EventPublisher {

	private final RabbitTemplate rabbitTemplate;
	private final String exchange;
	private final String routingKey;
	private final Duration confirmTimeout;

	public RabbitMqEventPublisher(
		RabbitTemplate rabbitTemplate,
		String exchange,
		String routingKey,
		Duration confirmTimeout
	) {
		this.rabbitTemplate = rabbitTemplate;
		this.exchange = exchange;
		this.routingKey = routingKey;
		this.confirmTimeout = confirmTimeout;
	}

	@Override
	public void publish(OutboxEvent event) {
		MessageProperties properties = new MessageProperties();
		properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		properties.setMessageId(event.id().toString());
		properties.setType(event.type());
		properties.setTimestamp(Date.from(event.occurredAt()));
		CorrelationData correlation = new CorrelationData(event.id().toString());
		rabbitTemplate.send(exchange, routingKey,
			new Message(event.payload().getBytes(StandardCharsets.UTF_8), properties), correlation);
		try {
			CorrelationData.Confirm confirm = correlation.getFuture()
				.get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
			if (!confirm.ack() || correlation.getReturned() != null) {
				throw new IllegalStateException("RabbitMQ가 이벤트 발행을 확인하지 않았습니다.");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("RabbitMQ 발행 확인 대기가 중단되었습니다.", exception);
		} catch (Exception exception) {
			throw new IllegalStateException("RabbitMQ 이벤트 발행 확인에 실패했습니다.", exception);
		}
	}
}
