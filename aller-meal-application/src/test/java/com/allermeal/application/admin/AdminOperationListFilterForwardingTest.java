package com.allermeal.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.allermeal.application.port.out.AdminAuditLogRepository;
import com.allermeal.application.port.out.AdminNotificationReprocessRequestRepository;
import com.allermeal.application.port.out.AdminRecollectionRequestRepository;
import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.DeadLetterEventRepository;
import com.allermeal.application.port.out.ExternalApiLogRepository;
import com.allermeal.application.port.out.MealCollectionDispatcher;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.NotificationRequestRepository;
import com.allermeal.application.port.out.OutboxEventRepository;
import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.common.EntityTimestamps;
import com.allermeal.domain.meal.MealItemLabelingStatus;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.notification.NotificationChannel;
import com.allermeal.domain.notification.NotificationReason;
import com.allermeal.domain.notification.NotificationStatus;
import com.allermeal.domain.outbox.OutboxEventStatus;
import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.EncryptedEmail;
import com.allermeal.domain.user.PasswordHash;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class AdminOperationListFilterForwardingTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T00:00:00Z"), ZoneOffset.UTC);

	@Test
	void collectionLabelingAndExternalListsForwardNormalizedServerFiltersAndPreserveLastEmptyPage() {
		AtomicReference<AdminCollectionJobQuery> collectionQuery = new AtomicReference<>();
		AtomicReference<AdminMealItemLabelingQuery> labelingQuery = new AtomicReference<>();
		AtomicReference<AdminExternalApiLogQuery> externalQuery = new AtomicReference<>();
		AdminCollectionJobPageResult expectedCollection = new AdminCollectionJobPageResult(List.of(), 2, 20, 21);
		AdminMealItemLabelingPageResult expectedLabeling = new AdminMealItemLabelingPageResult(List.of(), 2, 20, 21);
		AdminExternalApiLogPageResult expectedExternal = new AdminExternalApiLogPageResult(List.of(), 2, 20, 21);
		AdminCollectionFailureService service = new AdminCollectionFailureService(
			proxy(CollectionJobRepository.class, "findAdminPage", collectionQuery, expectedCollection),
			proxy(ExternalApiLogRepository.class, "findAdminPage", externalQuery, expectedExternal),
			proxy(MealRepository.class, "findAdminMealItemLabelings", labelingQuery, expectedLabeling),
			unavailable(AdminRecollectionRequestRepository.class), unavailable(MealCollectionDispatcher.class),
			unavailable(AdminAuditLogRepository.class), CLOCK);
		UUID schoolId = UUID.fromString("11111111-1111-1111-1111-111111111111");

		assertSame(expectedCollection, service.findCollectionJobs(admin(), 2, 20, " SUCCEEDED ", schoolId.toString(),
			"2026-09-26", " LUNCH ", " school "));
		assertSame(expectedLabeling, service.findMealItemLabelings(admin(), 2, 20, " UNKNOWN ", schoolId.toString(),
			"2026-09-26", " BREAKFAST ", " menu "));
		assertSame(expectedExternal, service.findExternalApiLogs(admin(), 2, 20, " NEIS ", " GET ", " SUCCESS ", " endpoint "));

		assertEquals(new AdminCollectionJobQuery(2, 20, CollectionJobStatus.SUCCEEDED, schoolId,
			LocalDate.parse("2026-09-26"), MealType.LUNCH, "school"), collectionQuery.get());
		assertEquals(new AdminMealItemLabelingQuery(2, 20, MealItemLabelingStatus.UNKNOWN, schoolId,
			LocalDate.parse("2026-09-26"), MealType.BREAKFAST, "menu"), labelingQuery.get());
		assertEquals(new AdminExternalApiLogQuery(2, 20, "NEIS", "GET", "SUCCESS", "endpoint"), externalQuery.get());
	}

	@Test
	void outboxDlqAndNotificationListsForwardFiltersAndPreserveEmptyLastPageTotal() {
		AtomicReference<AdminOutboxEventQuery> outboxQuery = new AtomicReference<>();
		AtomicReference<AdminDeadLetterEventQuery> dlqQuery = new AtomicReference<>();
		AtomicReference<AdminNotificationRequestQuery> notificationQuery = new AtomicReference<>();
		AdminOutboxEventPageResult expectedOutbox = new AdminOutboxEventPageResult(List.of(), 2, 20, 21);
		AdminDeadLetterEventPageResult expectedDlq = new AdminDeadLetterEventPageResult(List.of(), 2, 20, 21);
		AdminNotificationRequestPageResult expectedNotification = new AdminNotificationRequestPageResult(List.of(), 2, 20, 21);
		AdminNotificationFailureService service = new AdminNotificationFailureService(
			proxy(NotificationRequestRepository.class, "findAdminPage", notificationQuery, expectedNotification),
			proxy(DeadLetterEventRepository.class, "findAdminPage", dlqQuery, expectedDlq),
			unavailable(AdminNotificationReprocessRequestRepository.class),
			proxy(OutboxEventRepository.class, "findAdminPage", outboxQuery, expectedOutbox),
			unavailable(AdminAuditLogRepository.class), CLOCK);
		String notificationId = "22222222-2222-2222-2222-222222222222";

		assertSame(expectedOutbox, service.findOutboxEvents(admin(), 2, 20, " PENDING ", " MealCollected ", " event "));
		assertSame(expectedDlq, service.findDeadLetterEvents(admin(), 2, 20, " REPROCESSED ", " NotificationRequested ", " message "));
		assertSame(expectedNotification, service.findNotificationRequests(admin(), 2, 20, " RETRY_PENDING ", " EMAIL ",
			" RISK_PENDING ", notificationId));

		assertEquals(new AdminOutboxEventQuery(2, 20, OutboxEventStatus.PENDING, "MealCollected", "event"), outboxQuery.get());
		assertEquals(new AdminDeadLetterEventQuery(2, 20, AdminDeadLetterEventStatus.REPROCESSED,
			"NotificationRequested", "message"), dlqQuery.get());
		assertEquals(new AdminNotificationRequestQuery(2, 20, NotificationStatus.RETRY_PENDING, NotificationChannel.EMAIL,
			NotificationReason.RISK_PENDING, notificationId), notificationQuery.get());
	}

	private static User admin() {
		return User.restoreFromPersistence(new UserId(UUID.randomUUID()),
			new EncryptedEmail("v1:k:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA=="), new EmailSearchHash("a".repeat(64)),
			new PasswordHash("hash"), UserRole.ADMIN, UserStatus.ACTIVE, EmailVerificationStatus.VERIFIED,
			new EntityTimestamps(CLOCK.instant(), CLOCK.instant()), 0);
	}

	@SuppressWarnings("unchecked")
	private static <T, R> T proxy(Class<T> type, String methodName, AtomicReference<R> captured, Object result) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, arguments) -> {
			if (method.getName().equals(methodName)) {
				captured.set((R) arguments[0]);
				return result;
			}
			throw new AssertionError("unexpected repository invocation: " + method.getName());
		});
	}

	@SuppressWarnings("unchecked")
	private static <T> T unavailable(Class<T> type) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
			(proxy, method, arguments) -> { throw new AssertionError("unexpected repository invocation: " + method.getName()); });
	}
}
