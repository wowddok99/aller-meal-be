package com.allermeal.infra.consumer;

import com.allermeal.application.consumer.IdempotentEventConsumer;
import com.allermeal.application.consumer.IncomingEvent;
import com.allermeal.application.meal.MealAllergenLabelingService;
import com.allermeal.application.meal.MealLabelingEvents;
import com.allermeal.domain.meal.MealId;
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
@ConditionalOnProperty(name = "safe-meal.consumer.enabled", havingValue = "true")
public class WorkerEventListener {

	private static final Logger log = LoggerFactory.getLogger(WorkerEventListener.class);
	private static final String CONSUMER_NAME = "safe-meal-worker";

	private final IdempotentEventConsumer consumer;
	private final MealAllergenLabelingService mealAllergenLabelingService;
	private final RabbitMqRetryRouter retryRouter;
	private final TransactionTemplate transactionTemplate;
	private final ObjectMapper objectMapper;

	public WorkerEventListener(
		IdempotentEventConsumer consumer,
		MealAllergenLabelingService mealAllergenLabelingService,
		RabbitMqRetryRouter retryRouter,
		TransactionTemplate transactionTemplate,
		ObjectMapper objectMapper
	) {
		this.consumer = consumer;
		this.mealAllergenLabelingService = mealAllergenLabelingService;
		this.retryRouter = retryRouter;
		this.transactionTemplate = transactionTemplate;
		this.objectMapper = objectMapper;
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
		if (!MealLabelingEvents.MEAL_COLLECTED.equals(event.type())) {
			log.info("처리 대상이 아닌 이벤트를 건너뜁니다. eventId={}, eventType={}", event.id(), event.type());
			return;
		}
		consumer.consume(CONSUMER_NAME, event, this::handle);
	}

	private void handle(IncomingEvent event) {
		MealCollectedPayload payload = parseMealCollected(event.payload());
		boolean labeled = mealAllergenLabelingService.label(new MealId(payload.mealId()));
		log.info(
			"급식 알레르기 라벨링 이벤트를 처리했습니다. eventId={}, mealId={}, labeled={}",
			event.id(), payload.mealId(), labeled);
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

	private record MealCollectedPayload(UUID mealId) {
	}
}
