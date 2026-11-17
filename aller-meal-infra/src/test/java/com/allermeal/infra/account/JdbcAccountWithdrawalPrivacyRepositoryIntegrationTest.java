package com.allermeal.infra.account;

import static org.junit.jupiter.api.Assertions.*;

import com.allermeal.application.port.out.AccountWithdrawalPrivacyRepository;
import com.allermeal.infra.user.JpaUserRepository;
import com.allermeal.domain.user.*;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class JdbcAccountWithdrawalPrivacyRepositoryIntegrationTest {

	private static final Instant CREATED_AT = Instant.parse("2026-11-01T00:00:00Z");
	private static final Instant DUE_AT = Instant.parse("2026-11-08T00:00:00Z");
	private static final Instant DELETED_AT = DUE_AT.plusSeconds(60);
	private static final String ORIGINAL_EMAIL = "v1:test:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA==";
	private String containerId;
	private AnnotationConfigApplicationContext context;
	private JdbcTemplate jdbc;
	private AccountWithdrawalPrivacyRepository repository;
	private UUID schoolId;

	@BeforeAll
	void startIsolatedPostgresAndApplyRealMigrations() throws Exception {
		try {
			containerId = docker("run", "--rm", "-d", "--label", "allermeal.test=withdrawal-cleanup",
				"-e", "POSTGRES_DB=withdrawal_cleanup_it", "-e", "POSTGRES_USER=withdrawal_it",
				"-e", "POSTGRES_PASSWORD=isolated-test-password", "-p", "127.0.0.1::5432", "postgres:17.5-alpine").trim();
			String mapped = docker("port", containerId, "5432/tcp").trim();
			int port = Integer.parseInt(mapped.substring(mapped.lastIndexOf(':') + 1));
			var dataSource = new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:" + port + "/withdrawal_cleanup_it",
				"withdrawal_it", "isolated-test-password");
			jdbc = new JdbcTemplate(dataSource);
			awaitPostgres();
			Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("30").load().migrate();
			UUID existing = UUID.randomUUID();
			jdbc.update("""
				INSERT INTO users (user_id, encrypted_email, email_search_hash, password_hash, role, status,
				 email_verification_status, created_at, updated_at)
				VALUES (?, ?, ?, 'existing-password', 'MEMBER', 'ACTIVE', 'VERIFIED', ?, ?)
				""", existing, ORIGINAL_EMAIL, "a".repeat(64), timestamp(CREATED_AT), timestamp(CREATED_AT));
			var before = jdbc.queryForMap("SELECT * FROM users WHERE user_id = ?", existing);
			Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
			var after = jdbc.queryForMap("SELECT * FROM users WHERE user_id = ?", existing);
			assertNull(after.remove("display_name"));
			assertNull(after.remove("usage_type"));
			assertEquals(before, after);
			context = new AnnotationConfigApplicationContext();
			context.register(TransactionConfiguration.class);
			context.registerBean(DataSource.class, () -> dataSource);
			context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(dataSource));
			context.registerBean(JdbcAccountWithdrawalPrivacyRepository.class,
				() -> new JdbcAccountWithdrawalPrivacyRepository(JdbcClient.create(dataSource)));
			context.refresh();
			repository = context.getBean(AccountWithdrawalPrivacyRepository.class);
			assertTrue(AopUtils.isAopProxy(repository), "실제 @Transactional 프록시가 필요합니다.");
		} catch (Exception | AssertionError failure) {
			cleanup();
			throw failure;
		}
	}

	@BeforeEach
	void resetOnlyIsolatedFixtures() {
		jdbc.execute("TRUNCATE users, schools CASCADE");
		schoolId = UUID.randomUUID();
		jdbc.update("""
			INSERT INTO schools (school_id, neis_school_code, education_office_code, name, address, region, created_at, updated_at)
			VALUES (?, 'TEST', 'TEST', '테스트 학교', '테스트 주소', '테스트 지역', ?, ?)
			""", schoolId, timestamp(CREATED_AT), timestamp(CREATED_AT));
	}

	@Test
	void jpaPersistsAndReloadsProfileAcrossStateChanges() {
		try (var jpa = new AnnotationConfigApplicationContext()) {
			jpa.registerBean(DataSource.class, () -> jdbc.getDataSource());
			jpa.register(JpaTestConfiguration.class);
			jpa.registerBean(JpaUserRepository.class);
			jpa.refresh();
			var users = jpa.getBean(JpaUserRepository.class);
			User original = User.create(new UserId(UUID.randomUUID()), new EncryptedEmail(ORIGINAL_EMAIL),
				new EmailSearchHash("b".repeat(64)), new PasswordHash("password-hash"), CREATED_AT)
				.withProfile("표시 이름😀", "STUDENT");
			users.save(original);
			User loaded = users.findById(original.id()).orElseThrow();
			assertEquals("표시 이름😀", loaded.displayName());
			assertEquals("STUDENT", loaded.usageType());
			users.save(loaded.verifyEmail(CREATED_AT.plusSeconds(1)));
			User verified = users.findById(original.id()).orElseThrow();
			assertEquals("표시 이름😀", verified.displayName());
			assertEquals("STUDENT", verified.usageType());
		}
	}

	@Configuration
	@EnableTransactionManagement
	@EnableJpaRepositories(basePackages = "com.allermeal.infra.user")
	static class JpaTestConfiguration {
		@Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
			var factory = new LocalContainerEntityManagerFactoryBean();
			factory.setDataSource(dataSource);
			factory.setPackagesToScan("com.allermeal.infra.user");
			factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
			return factory;
		}
		@Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) {
			return new JpaTransactionManager(factory);
		}
	}

	@Test
	void completesExpiredReservationsIncludingExactDeadlineAndIsHarmlessOnRepeat() {
		UUID expired = fixture("WITHDRAWAL_PENDING", DUE_AT.minusSeconds(1));
		UUID exact = fixture("WITHDRAWAL_PENDING", DUE_AT);
		assertEquals(2, repository.deleteExpiredPersonalData(DUE_AT, DELETED_AT));
		assertDeleted(expired);
		assertDeleted(exact);
		var afterFirstRun = jdbc.queryForList("SELECT * FROM users ORDER BY user_id");

		assertEquals(0, repository.deleteExpiredPersonalData(DUE_AT, DELETED_AT.plusSeconds(60)));
		assertEquals(afterFirstRun, jdbc.queryForList("SELECT * FROM users ORDER BY user_id"));
		assertDeleted(expired);
		assertDeleted(exact);
	}

	@Test
	void leavesFutureAndCancelledReservationsUnchanged() {
		UUID future = fixture("WITHDRAWAL_PENDING", DUE_AT.plusSeconds(1));
		UUID cancelled = fixture("WITHDRAWAL_PENDING", DUE_AT.minusSeconds(1));
		jdbc.update("""
			UPDATE users SET status = 'ACTIVE', withdrawal_requested_at = NULL,
			withdrawal_due_at = NULL, withdrawal_masked_notification_count = NULL WHERE user_id = ?
			""", cancelled);
		var usersBefore = jdbc.queryForList("SELECT * FROM users ORDER BY user_id");
		var notificationsBefore = jdbc.queryForList("SELECT * FROM notification_requests ORDER BY notification_id");
		var childrenBefore = jdbc.queryForList("SELECT * FROM child_profiles ORDER BY child_id");

		assertEquals(0, repository.deleteExpiredPersonalData(DUE_AT, DELETED_AT));
		assertEquals(usersBefore, jdbc.queryForList("SELECT * FROM users ORDER BY user_id"));
		assertEquals(notificationsBefore, jdbc.queryForList("SELECT * FROM notification_requests ORDER BY notification_id"));
		assertEquals(childrenBefore, jdbc.queryForList("SELECT * FROM child_profiles ORDER BY child_id"));
		assertIntact(future);
		assertIntact(cancelled);
	}

	@Test
	void rollsBackEveryPrivacyChangeWhenFinalUserUpdateFails() {
		UUID user = fixture("WITHDRAWAL_PENDING", DUE_AT);
		var userBefore = jdbc.queryForMap("SELECT * FROM users WHERE user_id = ?", user);
		var notificationsBefore = jdbc.queryForList("SELECT * FROM notification_requests ORDER BY notification_id");
		var childBefore = jdbc.queryForMap("SELECT * FROM child_profiles WHERE user_id = ?", user);
		// 마지막 UPDATE에서만 실패시켜 앞선 개인정보 변경까지 실제 트랜잭션이 복구하는지 확인합니다.
		jdbc.execute("ALTER TABLE users ADD CONSTRAINT test_reject_disabled CHECK (status <> 'DISABLED')");
		try {
			var failure = assertThrows(DataIntegrityViolationException.class,
				() -> repository.deleteExpiredPersonalData(DUE_AT, DELETED_AT));
			assertTrue(failure.getMostSpecificCause().getMessage().contains("test_reject_disabled"));
			assertEquals(userBefore, jdbc.queryForMap("SELECT * FROM users WHERE user_id = ?", user));
			assertEquals(notificationsBefore, jdbc.queryForList("SELECT * FROM notification_requests ORDER BY notification_id"));
			assertEquals(childBefore, jdbc.queryForMap("SELECT * FROM child_profiles WHERE user_id = ?", user));
			assertIntact(user);
		} finally {
			jdbc.execute("ALTER TABLE users DROP CONSTRAINT test_reject_disabled");
		}
		assertEquals(1, repository.deleteExpiredPersonalData(DUE_AT, DELETED_AT));
		assertDeleted(user);
	}

	@Test
	void profileLocksLatestUserStateAgainstConcurrentWithdrawal() throws Exception {
		UUID user = UUID.randomUUID();
		jdbc.update("""
			INSERT INTO users (user_id, encrypted_email, email_search_hash, password_hash, role, status,
			 email_verification_status, created_at, updated_at, display_name)
			VALUES (?, ?, ?, 'password', 'MEMBER', 'ACTIVE', 'VERIFIED', ?, ?, '이름')
			""", user, ORIGINAL_EMAIL, "e".repeat(64), timestamp(CREATED_AT), timestamp(CREATED_AT));
		var entered = new java.util.concurrent.CountDownLatch(1);
		var release = new java.util.concurrent.CountDownLatch(1);
		var service = new com.allermeal.application.account.AccountProfileService(
			new JdbcAccountProfileRepository(JdbcClient.create(jdbc.getDataSource())), encrypted -> {
				entered.countDown();
				try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("대기 시간 초과"); }
				catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
				return "owner@example.test";
			}, java.time.Clock.fixed(DUE_AT, java.time.ZoneOffset.UTC));
		var tx = new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
		var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
		try {
			var read = executor.submit(() -> tx.execute(status -> service.findProfile(new UserId(user), 0)));
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			var updateStarted = new java.util.concurrent.CountDownLatch(1);
			var update = executor.submit(() -> { updateStarted.countDown(); return jdbc.update(
				"UPDATE users SET status = 'WITHDRAWAL_PENDING', withdrawal_requested_at = ?, withdrawal_due_at = ?, withdrawal_masked_notification_count = 0 WHERE user_id = ?", timestamp(CREATED_AT), timestamp(DUE_AT), user); });
			assertTrue(updateStarted.await(10, TimeUnit.SECONDS));
			assertThrows(java.util.concurrent.TimeoutException.class, () -> update.get(300, TimeUnit.MILLISECONDS));
			release.countDown();
			assertEquals("owner@example.test", read.get(10, TimeUnit.SECONDS).email());
			assertEquals(1, update.get(10, TimeUnit.SECONDS));
			assertThrows(com.allermeal.application.auth.UnauthorizedAccessException.class,
				() -> tx.execute(status -> service.findProfile(new UserId(user), 0)));
		} finally {
			release.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
		}
	}

	@Test
	void profileReadsCommittedRoleAndPendingStateAfterWaitingForRowLock() throws Exception {
		UUID user = fixture("WITHDRAWAL_PENDING", DUE_AT);
		var decryptions = new java.util.concurrent.atomic.AtomicInteger();
		var service = new com.allermeal.application.account.AccountProfileService(
			new JdbcAccountProfileRepository(JdbcClient.create(jdbc.getDataSource())), encrypted -> {
				decryptions.incrementAndGet();
				return "owner@example.test";
			}, java.time.Clock.fixed(CREATED_AT, java.time.ZoneOffset.UTC));
		var tx = new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
		var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
		try {
			var started = new java.util.concurrent.CountDownLatch(1);
			var pending = tx.execute(status -> {
				jdbc.update("UPDATE users SET role = 'ADMIN' WHERE user_id = ?", user);
				var read = executor.submit(() -> {
					started.countDown();
					return tx.execute(inner -> service.findProfile(new UserId(user), 0));
				});
				try {
					assertTrue(started.await(10, TimeUnit.SECONDS));
					assertThrows(java.util.concurrent.TimeoutException.class, () -> read.get(300, TimeUnit.MILLISECONDS));
				} catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
				return read;
			});
			var profile = pending.get(10, TimeUnit.SECONDS);
			assertEquals(UserRole.ADMIN, profile.role());
			assertEquals(UserStatus.WITHDRAWAL_PENDING, profile.userStatus());
			assertEquals(1, decryptions.get());
		} finally {
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
		}
	}

	@Test
	void profileRechecksChangedPendingDeadlineAfterWaitingForRowLock() throws Exception {
		UUID user = fixture("WITHDRAWAL_PENDING", DUE_AT);
		var decryptions = new java.util.concurrent.atomic.AtomicInteger();
		var service = new com.allermeal.application.account.AccountProfileService(
			new JdbcAccountProfileRepository(JdbcClient.create(jdbc.getDataSource())), encrypted -> {
				decryptions.incrementAndGet();
				return "owner@example.test";
			}, java.time.Clock.fixed(CREATED_AT.plusSeconds(1), java.time.ZoneOffset.UTC));
		var tx = new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
		var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
		try {
			var started = new java.util.concurrent.CountDownLatch(1);
			var pending = tx.execute(status -> {
				jdbc.update("UPDATE users SET withdrawal_due_at = ? WHERE user_id = ?", timestamp(CREATED_AT.plusSeconds(1)), user);
				var read = executor.submit(() -> {
					started.countDown();
					return tx.execute(inner -> service.findProfile(new UserId(user), 0));
				});
				try {
					assertTrue(started.await(10, TimeUnit.SECONDS));
					assertThrows(java.util.concurrent.TimeoutException.class, () -> read.get(300, TimeUnit.MILLISECONDS));
				} catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
				return read;
			});
			var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> pending.get(10, TimeUnit.SECONDS));
			assertInstanceOf(com.allermeal.application.auth.UnauthorizedAccessException.class, failure.getCause());
			assertEquals(0, decryptions.get());
		} finally {
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
		}
	}

	@Test
	void profileWaitsForPrivacyDeletionAndDoesNotDecryptMaskedData() throws Exception {
		UUID user = fixture("WITHDRAWAL_PENDING", DUE_AT);
		var decryptions = new java.util.concurrent.atomic.AtomicInteger();
		var service = new com.allermeal.application.account.AccountProfileService(
			new JdbcAccountProfileRepository(JdbcClient.create(jdbc.getDataSource())), encrypted -> {
				decryptions.incrementAndGet();
				return "owner@example.test";
			}, java.time.Clock.fixed(DUE_AT, java.time.ZoneOffset.UTC));
		var tx = new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
		var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
		try {
			var started = new java.util.concurrent.CountDownLatch(1);
			var pending = tx.execute(status -> {
				jdbc.queryForObject("SELECT user_id FROM users WHERE user_id = ? FOR UPDATE", UUID.class, user);
				var read = executor.submit(() -> {
					started.countDown();
					return tx.execute(inner -> service.findProfile(new UserId(user), 0));
				});
				try {
					assertTrue(started.await(10, TimeUnit.SECONDS));
					assertThrows(java.util.concurrent.TimeoutException.class, () -> read.get(300, TimeUnit.MILLISECONDS));
				} catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
				assertEquals(1, repository.deleteExpiredPersonalData(DUE_AT, DELETED_AT));
				return read;
			});
			var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> pending.get(10, TimeUnit.SECONDS));
			assertInstanceOf(com.allermeal.application.auth.UnauthorizedAccessException.class, failure.getCause());
			assertEquals(0, decryptions.get());
			assertDeleted(user);
		} finally {
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
		}
	}

	@Test
	void profileReadsEncryptedSignupEmailFromDatabase() {
		UUID user = UUID.randomUUID();
		var encryptor = new com.allermeal.infra.auth.AesGcmEmailEncryptor(new byte[32], "test", new java.security.SecureRandom());
		String encryptedEmail = encryptor.encrypt("owner@example.test");
		jdbc.update("""
			INSERT INTO users (user_id, encrypted_email, email_search_hash, password_hash, role, status,
			 email_verification_status, created_at, updated_at, display_name)
			VALUES (?, ?, ?, 'password', 'MEMBER', 'ACTIVE', 'VERIFIED', ?, ?, '이름')
			""", user, encryptedEmail, "f".repeat(64), timestamp(CREATED_AT), timestamp(CREATED_AT));
		var service = new com.allermeal.application.account.AccountProfileService(
			new JdbcAccountProfileRepository(JdbcClient.create(jdbc.getDataSource())), encryptor, java.time.Clock.fixed(DUE_AT, java.time.ZoneOffset.UTC));
		var tx = new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
		var profile = tx.execute(status -> service.findProfile(new UserId(user), 0));
		assertEquals("이름", profile.displayName());
		assertEquals("owner@example.test", profile.email());
	}

	@Test
	void profileDecryptFailureDoesNotReturnProfile() {
		UUID user = UUID.randomUUID();
		jdbc.update("""
			INSERT INTO users (user_id, encrypted_email, email_search_hash, password_hash, role, status,
			 email_verification_status, created_at, updated_at, display_name)
			VALUES (?, ?, ?, 'password', 'MEMBER', 'ACTIVE', 'VERIFIED', ?, ?, '이름')
			""", user, ORIGINAL_EMAIL, "f".repeat(64), timestamp(CREATED_AT), timestamp(CREATED_AT));
		var encryptor = new com.allermeal.infra.auth.AesGcmEmailEncryptor(new byte[32], "test", new java.security.SecureRandom());
		var service = new com.allermeal.application.account.AccountProfileService(
			new JdbcAccountProfileRepository(JdbcClient.create(jdbc.getDataSource())), encryptor, java.time.Clock.fixed(DUE_AT, java.time.ZoneOffset.UTC));
		var tx = new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
		assertThrows(IllegalStateException.class, () -> tx.execute(status -> service.findProfile(new UserId(user), 0)));
	}

	private UUID fixture(String status, Instant dueAt) {
		UUID user = UUID.randomUUID();
		UUID child = UUID.randomUUID();
		UUID target = UUID.randomUUID();
		String hash = UUID.randomUUID().toString().replace("-", "").repeat(2);
		jdbc.update("""
			INSERT INTO users (user_id, encrypted_email, email_search_hash, password_hash, role, status,
			 email_verification_status, created_at, updated_at, withdrawal_requested_at, withdrawal_due_at,
			 withdrawal_masked_notification_count, display_name, usage_type)
			VALUES (?, ?, ?, 'original-password-hash', 'MEMBER', ?, 'VERIFIED', ?, ?, ?, ?, 3, '표시 이름', 'PARENT')
			""", user, ORIGINAL_EMAIL, hash, status, timestamp(CREATED_AT), timestamp(CREATED_AT),
			timestamp(CREATED_AT), timestamp(dueAt));
		jdbc.update("""
			INSERT INTO child_profiles (child_id, user_id, name, grade, class_number, school_id, created_at, updated_at)
			VALUES (?, ?, '테스트 자녀', 3, 2, ?, ?, ?)
			""", child, user, schoolId, timestamp(CREATED_AT), timestamp(CREATED_AT));
		jdbc.update("INSERT INTO child_profile_allergens (child_id, allergen_code) VALUES (?, 1)", child);
		jdbc.update("""
			INSERT INTO notification_preferences (child_id, email_enabled, notification_time, timezone, created_at, updated_at)
			VALUES (?, true, '07:00', 'Asia/Seoul', ?, ?)
			""", child, timestamp(CREATED_AT), timestamp(CREATED_AT));
		jdbc.update("""
			INSERT INTO notification_targets (notification_target_id, child_id, user_id, school_id, notification_date,
			 notification_time, timezone, reason, meal_count, created_at, updated_at)
			VALUES (?, ?, ?, ?, '2026-11-08', '07:00', 'Asia/Seoul', 'NO_MEAL', 0, ?, ?)
			""", target, child, user, schoolId, timestamp(CREATED_AT), timestamp(CREATED_AT));
		for (String notificationStatus : List.of("PENDING", "SENDING", "RETRY_PENDING", "SENT")) {
			String key = UUID.randomUUID().toString().replace("-", "").repeat(2);
			boolean pending = notificationStatus.equals("PENDING");
			boolean retry = notificationStatus.equals("RETRY_PENDING");
			jdbc.update("""
				INSERT INTO notification_requests (notification_id, notification_target_id, child_id, user_id,
				 notification_date, channel, reason, dedup_key, correction_key, content_version, is_correction,
				 status, attempt_count, max_attempts, next_attempt_at, sent_at, failure_code, failure_message, created_at, updated_at)
				VALUES (?, ?, ?, ?, '2026-11-08', 'EMAIL', 'NO_MEAL', ?, ?, ?, false, ?, ?, 3, ?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), target, child, user, key, key, key, notificationStatus, pending ? 0 : 1,
				pending || retry ? timestamp(DUE_AT) : null, notificationStatus.equals("SENT") ? timestamp(CREATED_AT) : null,
				retry ? "SMTP_ERROR" : null, retry ? "발송 실패" : null, timestamp(CREATED_AT), timestamp(CREATED_AT));
		}
		return user;
	}

	private void assertDeleted(UUID user) {
		var account = jdbc.queryForMap("SELECT * FROM users WHERE user_id = ?", user);
		assertEquals("DISABLED", account.get("status"));
		assertNull(account.get("withdrawal_masked_notification_count"));
		assertNull(account.get("display_name"));
		assertNull(account.get("usage_type"));
		assertEquals(timestamp(DELETED_AT), account.get("personal_data_deleted_at"));
		assertEquals(timestamp(DELETED_AT), account.get("updated_at"));
		assertEquals(1L, account.get("version"));
		assertEquals("v1:withdrawal:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA==", account.get("encrypted_email"));
		assertEquals(user.toString().replace("-", "").repeat(2), account.get("email_search_hash"));
		assertEquals("withdrawn", account.get("password_hash"));
		var child = jdbc.queryForMap("SELECT * FROM child_profiles WHERE user_id = ?", user);
		assertEquals("삭제된 자녀", child.get("name"));
		assertEquals(1, ((Number) child.get("grade")).intValue());
		assertEquals(1, ((Number) child.get("class_number")).intValue());
		assertEquals(timestamp(DELETED_AT), child.get("updated_at"));
		assertEquals(0, count("child_profile_allergens", user));
		assertEquals(0, count("notification_preferences", user));
		assertEquals(3, jdbc.queryForObject("""
			SELECT count(*) FROM notification_requests WHERE user_id = ? AND status = 'CANCELED'
			AND failure_code = 'PERSONAL_DATA_DELETED' AND failure_message IS NULL
			AND next_attempt_at IS NULL AND sent_at IS NULL AND updated_at = ?
			""", Integer.class, user, timestamp(DELETED_AT)));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notification_requests WHERE user_id = ? AND status = 'SENT'",
			Integer.class, user));
	}

	private void assertIntact(UUID user) {
		assertEquals("표시 이름", jdbc.queryForObject("SELECT display_name FROM users WHERE user_id = ?", String.class, user));
		assertEquals("PARENT", jdbc.queryForObject("SELECT usage_type FROM users WHERE user_id = ?", String.class, user));
		assertEquals(ORIGINAL_EMAIL, jdbc.queryForObject("SELECT encrypted_email FROM users WHERE user_id = ?", String.class, user));
		assertEquals("테스트 자녀", jdbc.queryForObject("SELECT name FROM child_profiles WHERE user_id = ?", String.class, user));
		assertEquals(1, count("child_profile_allergens", user));
		assertEquals(1, count("notification_preferences", user));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notification_requests WHERE user_id = ? AND status = 'CANCELED'",
			Integer.class, user));
	}

	private int count(String table, UUID user) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table
			+ " WHERE child_id IN (SELECT child_id FROM child_profiles WHERE user_id = ?)", Integer.class, user);
	}

	private Timestamp timestamp(Instant instant) { return Timestamp.from(instant); }

	private void awaitPostgres() throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		RuntimeException lastFailure = null;
		while (System.nanoTime() < deadline) {
			try {
				if (Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class))) return;
			} catch (RuntimeException failure) { lastFailure = failure; }
			Thread.sleep(100);
		}
		throw new IllegalStateException("격리 PostgreSQL이 제한 시간 안에 준비되지 않았습니다.", lastFailure);
	}

	@AfterAll
	void cleanup() throws Exception {
		if (context != null) { context.close(); context = null; }
		if (containerId != null) {
			String ownedId = containerId;
			containerId = null;
			docker("stop", ownedId);
		}
	}

	private String docker(String... arguments) throws Exception {
		var command = new java.util.ArrayList<String>();
		command.add("docker");
		command.addAll(List.of(arguments));
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		if (!process.waitFor(60, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new IllegalStateException("Docker 명령이 시간 초과되었습니다.");
		}
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (process.exitValue() != 0) throw new IllegalStateException("격리 PostgreSQL Docker 명령에 실패했습니다: " + output);
		return output;
	}

	@Configuration(proxyBeanMethods = false)
	@EnableTransactionManagement
	private static class TransactionConfiguration { }
}
