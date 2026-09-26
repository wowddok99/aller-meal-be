package com.allermeal.infra.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allermeal.application.admin.AdminCollectionJobQuery;
import com.allermeal.application.admin.AdminDeadLetterEventQuery;
import com.allermeal.application.admin.AdminDeadLetterEventStatus;
import com.allermeal.application.admin.AdminExternalApiLogQuery;
import com.allermeal.application.admin.AdminMealItemLabelingQuery;
import com.allermeal.application.admin.AdminNotificationRequestQuery;
import com.allermeal.application.admin.AdminOutboxEventQuery;
import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.meal.MealItemLabelingStatus;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.notification.NotificationChannel;
import com.allermeal.domain.notification.NotificationReason;
import com.allermeal.domain.notification.NotificationStatus;
import com.allermeal.domain.outbox.OutboxEventStatus;
import com.allermeal.infra.collection.JdbcCollectionJobRepository;
import com.allermeal.infra.consumer.JdbcDeadLetterEventRepository;
import com.allermeal.infra.meal.JdbcMealRepository;
import com.allermeal.infra.notification.JdbcNotificationRequestRepository;
import com.allermeal.infra.outbox.JdbcOutboxEventRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class JdbcAdminOperationListRepositoryIntegrationTest {

	private static final Instant TIE_TIME = Instant.parse("2026-09-26T00:00:00Z");
	private static final LocalDate MEAL_DATE = LocalDate.of(2026, 9, 26);

	private Connection adminConnection;
	private SingleConnectionDataSource dataSource;
	private JdbcTemplate jdbc;
	private JdbcCollectionJobRepository collectionJobs;
	private JdbcMealRepository meals;
	private JdbcOutboxEventRepository outboxEvents;
	private JdbcDeadLetterEventRepository deadLetterEvents;
	private JdbcNotificationRequestRepository notifications;
	private JdbcExternalApiLogRepository externalApiLogs;
	private String schema;

	@BeforeAll
	void connectToComposePostgres() throws SQLException {
		String url = "jdbc:postgresql://" + setting("allermeal.it.postgres.host", "POSTGRES_PUBLISHED_HOST", "127.0.0.1")
			+ ":" + setting("allermeal.it.postgres.port", "POSTGRES_PUBLISHED_PORT", "5432")
			+ "/" + setting("allermeal.it.postgres.database", "POSTGRES_DB", "aller_meal");
		String user = setting("allermeal.it.postgres.user", "POSTGRES_USER", "aller_meal");
		String password = setting("allermeal.it.postgres.password", "POSTGRES_PASSWORD", "local-postgres-password");
		try {
			DriverManager.setLoginTimeout(3);
			adminConnection = DriverManager.getConnection(url, user, password);
		}
		catch (SQLException exception) {
			Assumptions.abort("Compose PostgreSQL이 없어 JDBC 통합 테스트를 건너뜁니다: " + exception.getSQLState());
			return;
		}
		schema = "admin_operation_list_it_" + UUID.randomUUID().toString().replace("-", "");
		try (var statement = adminConnection.createStatement()) {
			statement.execute("CREATE SCHEMA " + schema);
		}
		Connection testConnection = DriverManager.getConnection(url, user, password);
		try (var statement = testConnection.createStatement()) {
			statement.execute("SET search_path TO " + schema);
		}
		dataSource = new SingleConnectionDataSource(testConnection, true);
		jdbc = new JdbcTemplate(dataSource);
		createFixtureTables();
		JdbcClient jdbcClient = JdbcClient.create(dataSource);
		collectionJobs = new JdbcCollectionJobRepository(jdbcClient);
		meals = new JdbcMealRepository(jdbcClient);
		outboxEvents = new JdbcOutboxEventRepository(jdbc);
		deadLetterEvents = new JdbcDeadLetterEventRepository(jdbcClient);
		notifications = new JdbcNotificationRequestRepository(jdbcClient);
		externalApiLogs = new JdbcExternalApiLogRepository(jdbcClient);
	}

	@BeforeEach
	void clearFixture() {
		jdbc.execute("TRUNCATE external_api_logs, notification_requests, dead_letter_events, outbox_events, meal_items, meals, collection_jobs, schools");
	}

	@AfterAll
	void closeAndDropFixtureSchema() throws SQLException {
		if (dataSource != null) dataSource.destroy();
		if (adminConnection != null) {
			try (var statement = adminConnection.createStatement()) {
				if (schema != null) statement.execute("DROP SCHEMA " + schema + " CASCADE");
			}
			adminConnection.close();
		}
	}

	@Test
	void collectionJobsUseOneServerPredicateForCountItemsTieOrderAndEmptyLastPage() {
		UUID schoolId = school("목포항도여자중학교");
		UUID first = collectionJob(schoolId, "00000000-0000-0000-0000-000000000001", "FAILED", TIE_TIME);
		UUID second = collectionJob(schoolId, "00000000-0000-0000-0000-000000000002", "FAILED", TIE_TIME);
		collectionJob(schoolId, "00000000-0000-0000-0000-000000000003", "SUCCEEDED", TIE_TIME.plusSeconds(1));

		var filtered = collectionJobs.findAdminPage(new AdminCollectionJobQuery(1, 20, CollectionJobStatus.FAILED,
			schoolId, MEAL_DATE, MealType.LUNCH, "항도"));
		assertEquals(2, filtered.totalCount());
		assertEquals(java.util.List.of(second, first), filtered.items().stream().map(item -> item.collectionJobId().value()).toList());
		var lastPage = collectionJobs.findAdminPage(new AdminCollectionJobQuery(2, 2, CollectionJobStatus.FAILED,
			schoolId, MEAL_DATE, MealType.LUNCH, "항도"));
		assertTrue(lastPage.items().isEmpty());
		assertEquals(2, lastPage.totalCount());
	}

	@Test
	void mealItemLabelingsUseOneServerPredicateForCountItemsTieOrderAndEmptyLastPage() {
		UUID schoolId = school("목포중학교");
		UUID mealId = meal(schoolId);
		UUID first = mealItem(mealId, "00000000-0000-0000-0000-000000000011", "김치", "LABELED", TIE_TIME);
		UUID second = mealItem(mealId, "00000000-0000-0000-0000-000000000012", "김치찌개", "LABELED", TIE_TIME);
		mealItem(mealId, "00000000-0000-0000-0000-000000000013", "밥", "PENDING", TIE_TIME.plusSeconds(1));

		var filtered = meals.findAdminMealItemLabelings(new AdminMealItemLabelingQuery(1, 20,
			MealItemLabelingStatus.LABELED, schoolId, MEAL_DATE, MealType.LUNCH, "김치"));
		assertEquals(2, filtered.totalCount());
		assertEquals(java.util.List.of(second, first), filtered.items().stream().map(item -> item.mealItemId().value()).toList());
		var lastPage = meals.findAdminMealItemLabelings(new AdminMealItemLabelingQuery(2, 2,
			MealItemLabelingStatus.LABELED, schoolId, MEAL_DATE, MealType.LUNCH, "김치"));
		assertTrue(lastPage.items().isEmpty());
		assertEquals(2, lastPage.totalCount());
	}

	@Test
	void outboxEventsUseOneServerPredicateForCountItemsTieOrderAndEmptyLastPage() {
		UUID first = outboxEvent("00000000-0000-0000-0000-000000000021", "MealCollected", "PENDING", TIE_TIME);
		UUID second = outboxEvent("00000000-0000-0000-0000-000000000022", "MealCollected", "PENDING", TIE_TIME);
		outboxEvent("00000000-0000-0000-0000-000000000023", "NotificationRequested", "PUBLISHED", TIE_TIME.plusSeconds(1));

		var filtered = outboxEvents.findAdminPage(new AdminOutboxEventQuery(1, 20, OutboxEventStatus.PENDING,
			"MealCollected", "Meal"));
		assertEquals(2, filtered.totalCount());
		assertEquals(java.util.List.of(second, first), filtered.items().stream().map(item -> item.eventId()).toList());
		var lastPage = outboxEvents.findAdminPage(new AdminOutboxEventQuery(2, 2, OutboxEventStatus.PENDING,
			"MealCollected", "Meal"));
		assertTrue(lastPage.items().isEmpty());
		assertEquals(2, lastPage.totalCount());
	}

	@Test
	void deadLetterEventsUseOneServerPredicateForCountItemsTieOrderAndEmptyLastPage() {
		UUID first = deadLetter("00000000-0000-0000-0000-000000000031", "message-one", "NotificationRequested", "PENDING", TIE_TIME);
		UUID second = deadLetter("00000000-0000-0000-0000-000000000032", "message-two", "NotificationRequested", "PENDING", TIE_TIME);
		deadLetter("00000000-0000-0000-0000-000000000033", "message-three", "MealCollected", "REPROCESSED", TIE_TIME.plusSeconds(1));

		var filtered = deadLetterEvents.findAdminPage(new AdminDeadLetterEventQuery(1, 20,
			AdminDeadLetterEventStatus.PENDING, "NotificationRequested", "message"));
		assertEquals(2, filtered.totalCount());
		assertEquals(java.util.List.of(second, first), filtered.items().stream().map(item -> item.deadLetterEventId()).toList());
		var lastPage = deadLetterEvents.findAdminPage(new AdminDeadLetterEventQuery(2, 2,
			AdminDeadLetterEventStatus.PENDING, "NotificationRequested", "message"));
		assertTrue(lastPage.items().isEmpty());
		assertEquals(2, lastPage.totalCount());
	}

	@Test
	void notificationRequestsUseOneServerPredicateForCountItemsTieOrderAndEmptyLastPage() {
		UUID first = notification("00000000-0000-0000-0000-000000000041", "FAILED", TIE_TIME);
		UUID second = notification("00000000-0000-0000-0000-000000000042", "FAILED", TIE_TIME);
		notification("00000000-0000-0000-0000-000000000043", "PENDING", TIE_TIME.plusSeconds(1));

		var filtered = notifications.findAdminPage(new AdminNotificationRequestQuery(1, 20, NotificationStatus.FAILED,
			NotificationChannel.EMAIL, NotificationReason.RISK_DETECTED, second.toString()));
		assertEquals(1, filtered.totalCount());
		assertEquals(second, filtered.items().getFirst().notificationId().value());
		var lastPage = notifications.findAdminPage(new AdminNotificationRequestQuery(2, 2, NotificationStatus.FAILED,
			NotificationChannel.EMAIL, NotificationReason.RISK_DETECTED, null));
		assertTrue(lastPage.items().isEmpty());
		assertEquals(2, lastPage.totalCount());
	}

	@Test
	void externalApiLogsUseOneServerPredicateForCountItemsTieOrderAndEmptyLastPage() {
		UUID schoolId = school("광주고등학교");
		UUID first = externalLog(schoolId, "00000000-0000-0000-0000-000000000051", "NEIS", "GET", "SUCCESS", "/meal", TIE_TIME);
		UUID second = externalLog(schoolId, "00000000-0000-0000-0000-000000000052", "NEIS", "GET", "SUCCESS", "/meal", TIE_TIME);
		externalLog(schoolId, "00000000-0000-0000-0000-000000000053", "OTHER", "POST", "FAILURE", "/other", TIE_TIME.plusSeconds(1));

		var filtered = externalApiLogs.findAdminPage(new AdminExternalApiLogQuery(1, 20, "NEIS", "GET", "SUCCESS", "/meal"));
		assertEquals(2, filtered.totalCount());
		assertEquals(java.util.List.of(second, first), filtered.items().stream().map(item -> item.externalApiLogId()).toList());
		var lastPage = externalApiLogs.findAdminPage(new AdminExternalApiLogQuery(2, 2, "NEIS", "GET", "SUCCESS", "/meal"));
		assertTrue(lastPage.items().isEmpty());
		assertEquals(2, lastPage.totalCount());
	}

	private UUID school(String name) {
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO schools (school_id, name) VALUES (?, ?)", id, name);
		return id;
	}

	private UUID collectionJob(UUID schoolId, String id, String status, Instant updatedAt) {
		UUID value = UUID.fromString(id);
		jdbc.update("INSERT INTO collection_jobs VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", value, schoolId, MEAL_DATE,
			"LUNCH", status, 10L, 10L, null, null, status.equals("FAILED") ? "NEIS_ERROR" : null,
			status.equals("FAILED") ? "수집 실패" : null, Timestamp.from(updatedAt), Timestamp.from(updatedAt));
		return value;
	}

	private UUID meal(UUID schoolId) {
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO meals VALUES (?, ?, ?, ?)", id, schoolId, MEAL_DATE, "LUNCH");
		return id;
	}

	private UUID mealItem(UUID mealId, String id, String name, String status, Instant updatedAt) {
		UUID value = UUID.fromString(id);
		jdbc.update("INSERT INTO meal_items VALUES (?, ?, ?, ?, ?, ?, ?)", value, mealId, name, 0, status,
			Timestamp.from(updatedAt), Timestamp.from(updatedAt));
		return value;
	}

	private UUID outboxEvent(String id, String type, String status, Instant updatedAt) {
		UUID value = UUID.fromString(id);
		jdbc.update("INSERT INTO outbox_events VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, ?)", value, type, "{}", status,
			Timestamp.from(updatedAt), status.equals("PUBLISHED") ? Timestamp.from(updatedAt) : null,
			Timestamp.from(updatedAt), Timestamp.from(updatedAt));
		return value;
	}

	private UUID deadLetter(String id, String messageId, String type, String status, Instant updatedAt) {
		UUID value = UUID.fromString(id);
		jdbc.update("INSERT INTO dead_letter_events VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", value, messageId, type, "{}", 3,
			status, status.equals("REPROCESSED") ? UUID.randomUUID() : null,
			status.equals("REPROCESSED") ? Timestamp.from(updatedAt) : null, Timestamp.from(updatedAt), Timestamp.from(updatedAt));
		return value;
	}

	private UUID notification(String id, String status, Instant updatedAt) {
		UUID value = UUID.fromString(id);
		UUID targetId = UUID.randomUUID();
		jdbc.update("INSERT INTO notification_requests VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			value, targetId, UUID.randomUUID(), UUID.randomUUID(), MEAL_DATE, "EMAIL", "RISK_DETECTED", status,
			status.equals("PENDING") ? 0 : 1, 3, status.equals("PENDING") ? Timestamp.from(updatedAt) : null, null,
			status.equals("PENDING") ? null : "MAIL_FAILURE", status.equals("PENDING") ? null : "발송 실패",
			Timestamp.from(updatedAt), Timestamp.from(updatedAt), "unused", "unused");
		return value;
	}

	private UUID externalLog(UUID schoolId, String id, String provider, String method, String outcome, String endpoint, Instant createdAt) {
		UUID value = UUID.fromString(id);
		jdbc.update("INSERT INTO external_api_logs VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", value, provider,
			"MEAL_FETCH", schoolId, MEAL_DATE, "LUNCH", method, endpoint, 200, outcome, null, 10L, Timestamp.from(createdAt));
		return value;
	}

	private void createFixtureTables() {
		jdbc.execute("CREATE TABLE schools (school_id UUID PRIMARY KEY, name VARCHAR(200) NOT NULL)");
		jdbc.execute("CREATE TABLE collection_jobs (collection_job_id UUID PRIMARY KEY, school_id UUID NOT NULL, meal_date DATE NOT NULL, meal_type VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL, response_time_millis BIGINT, collection_duration_millis BIGINT, lease_until TIMESTAMPTZ, raw_object_id UUID, failure_code VARCHAR(100), failure_message VARCHAR(1000), created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL)");
		jdbc.execute("CREATE TABLE meals (meal_id UUID PRIMARY KEY, school_id UUID NOT NULL, meal_date DATE NOT NULL, meal_type VARCHAR(20) NOT NULL)");
		jdbc.execute("CREATE TABLE meal_items (meal_item_id UUID PRIMARY KEY, meal_id UUID NOT NULL, name VARCHAR(300) NOT NULL, display_order INT NOT NULL, labeling_status VARCHAR(30) NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL)");
		jdbc.execute("CREATE TABLE outbox_events (event_id UUID PRIMARY KEY, event_type VARCHAR(100) NOT NULL, payload JSONB NOT NULL, status VARCHAR(20) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL)");
		jdbc.execute("CREATE TABLE dead_letter_events (dead_letter_event_id UUID PRIMARY KEY, message_id VARCHAR(100) NOT NULL, event_type VARCHAR(100) NOT NULL, payload TEXT NOT NULL, retry_count INT NOT NULL, status VARCHAR(30) NOT NULL, reprocessed_by_user_id UUID, reprocessed_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL)");
		jdbc.execute("CREATE TABLE notification_requests (notification_id UUID PRIMARY KEY, notification_target_id UUID NOT NULL, child_id UUID NOT NULL, user_id UUID NOT NULL, notification_date DATE NOT NULL, channel VARCHAR(30) NOT NULL, reason VARCHAR(30) NOT NULL, status VARCHAR(30) NOT NULL, attempt_count SMALLINT NOT NULL, max_attempts SMALLINT NOT NULL, next_attempt_at TIMESTAMPTZ, sent_at TIMESTAMPTZ, failure_code VARCHAR(100), failure_message VARCHAR(1000), created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, ignored_one VARCHAR(20), ignored_two VARCHAR(20))");
		jdbc.execute("CREATE TABLE external_api_logs (external_api_log_id UUID PRIMARY KEY, provider VARCHAR(100) NOT NULL, operation VARCHAR(100) NOT NULL, school_id UUID NOT NULL, meal_date DATE NOT NULL, meal_type VARCHAR(20) NOT NULL, method VARCHAR(20) NOT NULL, endpoint VARCHAR(500) NOT NULL, http_status INT NOT NULL, outcome VARCHAR(50) NOT NULL, failure_code VARCHAR(100), response_time_millis BIGINT, created_at TIMESTAMPTZ NOT NULL)");
	}

	private String setting(String property, String environment, String fallback) {
		return System.getProperty(property, System.getenv().getOrDefault(environment, fallback));
	}
}
