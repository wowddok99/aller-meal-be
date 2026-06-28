package com.allermeal.infra.consumer;

import com.allermeal.application.consumer.IdempotentEventConsumer;
import com.allermeal.application.consumer.IncomingEvent;
import com.allermeal.application.meal.MealAllergenLabelingService;
import com.allermeal.application.meal.MealLabelingEvents;
import com.allermeal.application.notification.NotificationDeliveryResult;
import com.allermeal.application.notification.NotificationDeliveryService;
import com.allermeal.application.notification.NotificationRequestEvents;
import com.allermeal.domain.meal.MealId;
import com.allermeal.domain.notification.NotificationId;
import com.allermeal.domain.notification.NotificationStatus;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "aller-meal.consumer.enabled", havingValue = "true")
public class WorkerEventListener {

	private static final Logger log = LoggerFactory.getLogger(WorkerEventListener.class);
	private static final String CONSUMER_NAME = "aller-meal-worker";

	private final IdempotentEventConsumer consumer;
	private final MealAllergenLabelingService mealAllergenLabelingService;
	private final NotificationDeliveryService notificationDeliveryService;
	private final RabbitMqRetryRouter retryRouter;
	private final TransactionTemplate transactionTemplate;
	private final ObjectMapper objectMapper;

	public WorkerEventListener(
		IdempotentEventConsumer consumer,
		MealAllergenLabelingService mealAllergenLabelingService,
		NotificationDeliveryService notificationDeliveryService,
		RabbitMqRetryRouter retryRouter,
		TransactionTemplate transactionTemplate,
		ObjectMapper objectMapper
	) {
		this.consumer = consumer;
		this.mealAllergenLabelingService = mealAllergenLabelingService;
		this.notificationDeliveryService = notificationDeliveryService;
		this.retryRouter = retryRouter;
		this.transactionTemplate = transactionTemplate;
		this.objectMapper = objectMapper;
	}

	@RabbitListener(queues = "${aller-meal.rabbitmq.events.queue}")
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
		if (!isSupported(event.type())) {
			log.info("처리 대상이 아닌 이벤트를 건너뜁니다. eventId={}, eventType={}", event.id(), event.type());
			return;
		}
		consumer.consume(CONSUMER_NAME, event, this::handle);
	}

	private boolean isSupported(String eventType) {
		return MealLabelingEvents.MEAL_COLLECTED.equals(eventType)
			|| NotificationRequestEvents.NOTIFICATION_REQUESTED.equals(eventType);
	}

	private void handle(IncomingEvent event) {
		if (MealLabelingEvents.MEAL_COLLECTED.equals(event.type())) {
			handleMealCollected(event);
			return;
		}
		if (NotificationRequestEvents.NOTIFICATION_REQUESTED.equals(event.type())) {
			handleNotificationRequested(event);
			return;
		}
		throw new IllegalArgumentException("지원하지 않는 이벤트입니다. eventType=" + event.type());
	}

	private void handleMealCollected(IncomingEvent event) {
		MealCollectedPayload payload = parseMealCollected(event.payload());
		boolean labeled = mealAllergenLabelingService.label(new MealId(payload.mealId()));
		log.info(
			"급식 알레르기 라벨링 이벤트를 처리했습니다. eventId={}, mealId={}, labeled={}",
			event.id(), payload.mealId(), labeled);
	}

	private void handleNotificationRequested(IncomingEvent event) {
		NotificationRequestedPayload payload = parseNotificationRequested(event.payload());
		NotificationDeliveryResult result = notificationDeliveryService.deliver(
			new NotificationId(payload.notificationId()));
		log.info(
			"알림 발송 이벤트를 처리했습니다. eventId={}, notificationId={}, status={}, attemptCount={}",
			event.id(), payload.notificationId(), result.status(), result.attemptCount());
		if (result.status() == NotificationStatus.RETRY_PENDING) {
			throw new IllegalStateException("알림 발송 재시도가 필요합니다.");
		}
		if (result.status() == NotificationStatus.FAILED) {
			throw new DeadLetterRoutingException("알림 발송 최대 시도 횟수를 초과했습니다.");
		}
	}

	private MealCollectedPayload parseMealCollected(String payload) {
		try {
			JsonNode root = objectMapper.readTree(payload);
			JsonNode mealId = root == null ? null : root.get("mealId");
			if (mealId == null || !mealId.isString() || mealId.textValue().isBlank()) {
				throw new IllegalArgumentException("mealId가 없습니다.");
			}
			return new MealCollectedPayload(UUID.fromString(mealId.textValue()));
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("MealCollected payload가 올바르지 않습니다.", exception);
		}
	}

	private NotificationRequestedPayload parseNotificationRequested(String payload) {
		try {
			JsonNode root = objectMapper.readTree(payload);
			JsonNode notificationId = root == null ? null : root.get("notificationId");
			if (notificationId == null || !notificationId.isString() || notificationId.textValue().isBlank()) {
				throw new IllegalArgumentException("notificationId가 없습니다.");
			}
			return new NotificationRequestedPayload(UUID.fromString(notificationId.textValue()));
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("NotificationRequested payload가 올바르지 않습니다.", exception);
		}
	}

	private record MealCollectedPayload(UUID mealId) {
	}

	private record NotificationRequestedPayload(UUID notificationId) {
	}
}
