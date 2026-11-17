package com.allermeal.infra.notification;

import static org.junit.jupiter.api.Assertions.*;

import com.allermeal.application.port.out.result.NotificationMailContextResult;
import com.allermeal.application.notification.NotificationDeliveryService;
import com.allermeal.application.notification.NotificationMealSummaryService;
import com.allermeal.application.notification.NotificationRequestKeyFactory;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.result.PendingNotificationTargetResult;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.notification.*;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.*;
import com.allermeal.domain.risk.MealRiskLevel;
import com.allermeal.infra.child.JdbcChildProfileRepository;
import com.allermeal.infra.child.JdbcChildAllergenRepository;
import com.allermeal.infra.meal.JdbcMealRepository;
import com.allermeal.infra.allergen.JdbcAllergenRepository;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class JdbcNotificationMailContextRepositoryIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");
	private String container;
	private DriverManagerDataSource dataSource;
	private JdbcTemplate jdbc;
	private TransactionTemplate transaction;
	private JdbcNotificationMailContextRepository repository;
	private UUID owner, child, school, target;

	@BeforeAll
	void startOwnedDatabase() throws Exception {
		try {
			container = docker("run", "--rm", "-d", "--label", "allermeal.test=notification-mail-context",
				"-e", "POSTGRES_DB=notification_mail_it", "-e", "POSTGRES_USER=mail_it",
				"-e", "POSTGRES_PASSWORD=isolated-test-password", "-p", "127.0.0.1::5432", "postgres:17.5-alpine").trim();
			String mapped = docker("port", container, "5432/tcp").trim();
			int port = Integer.parseInt(mapped.substring(mapped.lastIndexOf(':') + 1));
			dataSource = new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:" + port + "/notification_mail_it",
				"mail_it", "isolated-test-password");
			jdbc = new JdbcTemplate(dataSource);
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
			while (true) {
				try { jdbc.queryForObject("SELECT 1", Integer.class); break; }
				catch (RuntimeException failure) {
					if (System.nanoTime() >= deadline) throw failure;
					Thread.sleep(100);
				}
			}
			Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
			transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
			repository = new JdbcNotificationMailContextRepository(JdbcClient.create(dataSource));
		} catch (Exception | AssertionError failure) { cleanup(); throw failure; }
	}

	@BeforeEach
	void createOwnedFixtures() {
		jdbc.execute("TRUNCATE users, schools CASCADE");
		owner = UUID.randomUUID(); child = UUID.randomUUID(); school = UUID.randomUUID(); target = UUID.randomUUID();
		jdbc.update("""
			INSERT INTO users(user_id, encrypted_email, email_search_hash, password_hash, role, status,
			 email_verification_status, created_at, updated_at)
			VALUES (?, 'v1:test:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA==', ?, 'hash', 'MEMBER', 'ACTIVE', 'VERIFIED', ?, ?)
			""", owner, "0".repeat(64), Timestamp.from(NOW), Timestamp.from(NOW));
		jdbc.update("""
			INSERT INTO schools(school_id, neis_school_code, education_office_code, name, address, region, created_at, updated_at)
			VALUES (?, 'TEST', 'TEST', '알림 학교', '주소', '지역', ?, ?)
			""", school, Timestamp.from(NOW), Timestamp.from(NOW));
		jdbc.update("""
			INSERT INTO child_profiles(child_id, user_id, name, grade, class_number, school_id, created_at, updated_at)
			VALUES (?, ?, '소유 자녀', 1, 1, ?, ?, ?)
			""", child, owner, school, Timestamp.from(NOW), Timestamp.from(NOW));
		jdbc.update("""
			INSERT INTO notification_targets(notification_target_id, child_id, user_id, school_id, notification_date,
			 notification_time, timezone, reason, risk_level, risk_version, meal_count, created_at, updated_at)
			VALUES (?, ?, ?, ?, '2026-11-13', '08:00', 'Asia/Seoul', 'NO_RISK', 'SAFE', ?, 1, ?, ?)
			""", target, child, owner, school, "a".repeat(64), Timestamp.from(NOW), Timestamp.from(NOW));
	}

	@Test
	void returnsOnlyMatchingChildOwnerSchoolAndTargetDate() {
		NotificationMailContextResult context = transaction.execute(status -> repository.findForDelivery(request(owner, child, target, LocalDate.of(2026, 11, 13))).orElseThrow());
		assertEquals("소유 자녀", context.childName());
		assertEquals("알림 학교", context.schoolName());
		assertEquals(school, context.schoolId().value());
		assertEquals(owner, context.target().ownerId().value());
		assertEquals("a".repeat(64), context.target().riskVersion());
		assertMissing(request(UUID.randomUUID(), child, target, LocalDate.of(2026, 11, 13)));
		assertMissing(request(owner, UUID.randomUUID(), target, LocalDate.of(2026, 11, 13)));
		assertMissing(request(owner, child, UUID.randomUUID(), LocalDate.of(2026, 11, 13)));
		assertMissing(request(owner, child, target, LocalDate.of(2026, 11, 14)));
	}

	@Test
	void deletedChildOrTransferredOwnershipDoesNotProduceMailContext() {
		jdbc.update("DELETE FROM child_profiles WHERE child_id = ?", child);
		assertMissing(request(owner, child, target, LocalDate.of(2026, 11, 13)));
	}

	@Test
	void targetOwnerMismatchAndChangedSchoolDoNotProduceMailContext() {
		UUID anotherSchool = UUID.randomUUID();
		jdbc.update("""
			INSERT INTO schools(school_id, neis_school_code, education_office_code, name, address, region, created_at, updated_at)
			VALUES (?, 'OTHER', 'TEST', '전학 학교', '주소', '지역', ?, ?)
			""", anotherSchool, Timestamp.from(NOW), Timestamp.from(NOW));
		jdbc.update("UPDATE child_profiles SET school_id = ? WHERE child_id = ?", anotherSchool, child);
		assertMissing(request(owner, child, target, LocalDate.of(2026, 11, 13)));
	}

	@Test
	void childOwnershipAndTargetOwnershipMustBothMatchRequestOwner() {
		UUID anotherOwner = UUID.randomUUID();
		jdbc.update("""
			INSERT INTO users(user_id, encrypted_email, email_search_hash, password_hash, role, status,
			 email_verification_status, created_at, updated_at)
			VALUES (?, 'v1:test:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA==', ?, 'hash', 'MEMBER', 'ACTIVE', 'VERIFIED', ?, ?)
			""", anotherOwner, "1".repeat(64), Timestamp.from(NOW), Timestamp.from(NOW));
		jdbc.update("UPDATE child_profiles SET user_id = ? WHERE child_id = ?", anotherOwner, child);
		assertMissing(request(owner, child, target, LocalDate.of(2026, 11, 13)));
		jdbc.update("UPDATE child_profiles SET user_id = ? WHERE child_id = ?", owner, child);
		jdbc.update("UPDATE notification_targets SET user_id = ? WHERE notification_target_id = ?", anotherOwner, target);
		assertMissing(request(owner, child, target, LocalDate.of(2026, 11, 13)));
	}

	@Test
	void contextLocksChildAndSchoolUntilSendingTransactionEnds() {
		transaction.executeWithoutResult(status -> {
			assertTrue(repository.findForDelivery(request(owner, child, target, LocalDate.of(2026, 11, 13))).isPresent());
			try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
				statement.execute("SET lock_timeout = '150ms'");
				var childLocked = assertThrows(java.sql.SQLException.class,
					() -> statement.executeUpdate("UPDATE child_profiles SET name = '변경' WHERE child_id = '" + child + "'"));
				assertEquals("55P03", childLocked.getSQLState());
				var schoolLocked = assertThrows(java.sql.SQLException.class,
					() -> statement.executeUpdate("UPDATE schools SET name = '변경' WHERE school_id = '" + school + "'"));
				assertEquals("55P03", schoolLocked.getSQLState());
				var targetLocked = assertThrows(java.sql.SQLException.class,
					() -> statement.executeUpdate("UPDATE notification_targets SET reason = 'RISK_PENDING' WHERE notification_target_id = '" + target + "'"));
				assertEquals("55P03", targetLocked.getSQLState());
			} catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
		});
		assertEquals(1, jdbc.update("UPDATE child_profiles SET name = '변경' WHERE child_id = ?", child));
	}

	@Test
	void deletionAlreadyCascadingToNotificationRequestDoesNotDeadlockWithActualDelivery() throws Exception {
		JdbcClient client = JdbcClient.create(dataSource);
		var requests = new JdbcNotificationRequestRepository(client);
		var snapshot = new PendingNotificationTargetResult(target, new ChildProfileId(child), new UserId(owner),
			LocalDate.of(2026, 11, 13), NotificationReason.NO_RISK, MealRiskLevel.SAFE, "a".repeat(64), 1);
		var request = NotificationRequest.pending(new NotificationId(UUID.randomUUID()), target, snapshot.childProfileId(), snapshot.ownerId(),
			snapshot.notificationDate(), NotificationChannel.EMAIL, snapshot.reason(), NotificationRequestKeyFactory.dedupKey(snapshot, NotificationChannel.EMAIL),
			NotificationRequestKeyFactory.correctionKey(snapshot, NotificationChannel.EMAIL), NotificationRequestKeyFactory.contentVersion(snapshot),
			false, null, 3, NOW);
		transaction.executeWithoutResult(status -> requests.saveCorrectionAware(request));
		AtomicInteger sent = new AtomicInteger();
		var user = User.create(new UserId(owner), new EncryptedEmail("v1:test:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA=="),
			new EmailSearchHash("0".repeat(64)), new PasswordHash("hash"), NOW).verifyEmail(NOW);
		UserRepository users = new UserRepository() {
			public User save(User next) { throw new UnsupportedOperationException(); }
			public Optional<User> findById(UserId id) { return id.equals(user.id()) ? Optional.of(user) : Optional.empty(); }
			public Optional<User> findByEmailSearchHash(EmailSearchHash hash) { return Optional.empty(); }
			public boolean existsByEmailSearchHash(EmailSearchHash hash) { return false; }
			public boolean existsByRole(UserRole role) { return false; }
		};
		var delivery = new NotificationDeliveryService(requests, users, encrypted -> "owner@example.test", command -> sent.incrementAndGet(),
			Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5), repository,
			new NotificationMealSummaryService(new JdbcMealRepository(client), new JdbcChildAllergenRepository(client), new JdbcAllergenRepository(client)));
		// 전용 DB의 cascade DELETE를 request 행 잠금 직전에서만 멈춘다. 실제 삭제 경로는 운영 repository 그대로 실행한다.
		long gate = 2026111301L;
		jdbc.execute("CREATE FUNCTION pause_mail_request_delete() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM pg_advisory_xact_lock(" + gate + "); RETURN NULL; END $$");
		jdbc.execute("CREATE TRIGGER pause_mail_request_delete BEFORE DELETE ON notification_requests FOR EACH STATEMENT EXECUTE FUNCTION pause_mail_request_delete()");
		var executor = Executors.newFixedThreadPool(2);
		try (var gateConnection = dataSource.getConnection(); var gateStatement = gateConnection.createStatement()) {
			int gatePid;
			try (var result = gateStatement.executeQuery("SELECT pg_backend_pid(), pg_advisory_lock(" + gate + ")")) {
				assertTrue(result.next()); gatePid = result.getInt(1);
			}
			AtomicInteger deletePid = new AtomicInteger(), deliveryPid = new AtomicInteger();
			CountDownLatch deleteStarted = new CountDownLatch(1), deliveryStarted = new CountDownLatch(1);
			var deletion = executor.submit(() -> transaction.execute(status -> {
				jdbc.execute("SET LOCAL statement_timeout = '8s'");
				deletePid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
				deleteStarted.countDown();
				return new JdbcChildProfileRepository(client).deleteByIdAndOwnerId(new ChildProfileId(child), new UserId(owner));
			}));
			assertTrue(deleteStarted.await(3, TimeUnit.SECONDS));
			awaitBlocking(deletePid.get(), gatePid);
			var sending = executor.submit(() -> transaction.execute(status -> {
				jdbc.execute("SET LOCAL statement_timeout = '8s'");
				deliveryPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
				deliveryStarted.countDown();
				return delivery.deliver(request.id());
			}));
			assertTrue(deliveryStarted.await(3, TimeUnit.SECONDS));
			awaitBlocking(deliveryPid.get(), deletePid.get());
			gateStatement.execute("SELECT pg_advisory_unlock(" + gate + ")");
			assertEquals(Boolean.TRUE, deletion.get(5, TimeUnit.SECONDS));
			assertEquals(NotificationStatus.CANCELED, sending.get(5, TimeUnit.SECONDS).status());
			assertEquals(0, sent.get());
			assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM child_profiles", Integer.class));
			assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notification_targets", Integer.class));
			assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notification_requests", Integer.class));
		} finally {
			executor.shutdown();
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
			jdbc.execute("DROP TRIGGER pause_mail_request_delete ON notification_requests");
			jdbc.execute("DROP FUNCTION pause_mail_request_delete()");
		}
	}

	private void awaitBlocking(int blockedPid, int blockerPid) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
		while (System.nanoTime() < deadline) {
			if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, blockerPid, blockedPid))) return;
			Thread.sleep(10);
		}
		fail("실제 PostgreSQL 잠금 경합이 관찰되지 않았습니다.");
	}

	private void assertMissing(NotificationRequest request) {
		boolean missing = transaction.execute(status -> repository.findForDelivery(request).isEmpty());
		assertTrue(missing);
	}

	private NotificationRequest request(UUID ownerId, UUID childId, UUID targetId, LocalDate date) {
		return NotificationRequest.pending(new NotificationId(UUID.randomUUID()), targetId, new ChildProfileId(childId),
			new UserId(ownerId), date, NotificationChannel.EMAIL, NotificationReason.NO_RISK,
			"b".repeat(64), "c".repeat(64), "d".repeat(64), false, null, 3, NOW);
	}

	@AfterAll
	void cleanup() throws Exception {
		if (container != null) {
			String owned = container; container = null;
			assertEquals("notification-mail-context", docker("inspect", "-f", "{{index .Config.Labels \"allermeal.test\"}}", owned).trim());
			docker("stop", owned);
		}
	}

	private String docker(String... arguments) throws Exception {
		var command = new ArrayList<String>(); command.add("docker"); command.addAll(List.of(arguments));
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		if (!process.waitFor(60, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Docker 명령 시간 초과"); }
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (process.exitValue() != 0) throw new IllegalStateException("격리 DB 명령 실패: " + output);
		return output;
	}
}
