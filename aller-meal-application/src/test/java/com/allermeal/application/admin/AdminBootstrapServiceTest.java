package com.allermeal.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allermeal.application.auth.DuplicateEmailException;
import com.allermeal.application.port.out.AdminAuditLogRepository;
import com.allermeal.application.port.out.AdminBootstrapLockRepository;
import com.allermeal.application.port.out.EmailEncryptor;
import com.allermeal.application.port.out.EmailSearchHasher;
import com.allermeal.application.port.out.PasswordHasher;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.command.AdminAuditLogCommand;
import com.allermeal.domain.common.EntityTimestamps;
import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.EncryptedEmail;
import com.allermeal.domain.user.PasswordHash;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AdminBootstrapServiceTest {

	private static final Instant NOW = Instant.parse("2026-06-28T09:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
	private static final String ADMIN_EMAIL = "admin@allermeal.local";
	private static final String ADMIN_HASH = "a".repeat(64);
	private static final String OTHER_HASH = "b".repeat(64);

	@Test
	void skipsExistingMemberPromotionWhenAnyAdminAlreadyExists() {
		FakeUsers users = new FakeUsers();
		User target = user(ADMIN_HASH, UserRole.MEMBER, UserStatus.ACTIVE, EmailVerificationStatus.UNVERIFIED);
		User existingAdmin = user(OTHER_HASH, UserRole.ADMIN, UserStatus.ACTIVE, EmailVerificationStatus.VERIFIED);
		users.put(target);
		users.put(existingAdmin);
		FakeAdminAuditLogs auditLogs = new FakeAdminAuditLogs();

		Optional<AdminBootstrapResult> result = service(users, new FakeBootstrapLock(users), auditLogs).bootstrap();

		assertTrue(result.isPresent());
		assertFalse(result.get().changed());
		assertEquals("BOOTSTRAP_ADMIN_SKIPPED_EXISTING_ADMIN", result.get().action());
		assertEquals(UserRole.MEMBER, users.findById(target.id()).orElseThrow().role());
		assertTrue(auditLogs.commands.isEmpty());
	}

	@Test
	void returnsNoopWhenBootstrapTargetAlreadyAdmin() {
		FakeUsers users = new FakeUsers();
		User target = user(ADMIN_HASH, UserRole.ADMIN, UserStatus.ACTIVE, EmailVerificationStatus.VERIFIED);
		users.put(target);
		FakeAdminAuditLogs auditLogs = new FakeAdminAuditLogs();
		FakeBootstrapLock lock = new FakeBootstrapLock(users);

		Optional<AdminBootstrapResult> result = service(users, lock, auditLogs).bootstrap();

		assertTrue(result.isPresent());
		assertFalse(result.get().changed());
		assertEquals(target.id(), result.get().userId());
		assertEquals("BOOTSTRAP_ADMIN_ALREADY_READY", result.get().action());
		assertFalse(lock.acquired);
		assertTrue(auditLogs.commands.isEmpty());
	}

	@Test
	void checksExistingAdminAfterBootstrapLockBeforeCreatingFirstAdmin() {
		FakeUsers users = new FakeUsers();
		users.requireLockBeforeRoleCheck = true;
		FakeAdminAuditLogs auditLogs = new FakeAdminAuditLogs();

		Optional<AdminBootstrapResult> result = service(users, new FakeBootstrapLock(users), auditLogs).bootstrap();

		assertTrue(result.isPresent());
		assertTrue(result.get().changed());
		assertEquals("BOOTSTRAP_ADMIN_CREATED", result.get().action());
		assertTrue(users.existsByRole(UserRole.ADMIN));
		assertEquals(1, auditLogs.commands.size());
		assertEquals("BOOTSTRAP_ADMIN_CREATED", auditLogs.commands.getFirst().action());
	}

	@Test
	void rejectsNonActiveBootstrapTarget() {
		FakeUsers users = new FakeUsers();
		users.put(user(ADMIN_HASH, UserRole.MEMBER, UserStatus.WITHDRAWAL_PENDING, EmailVerificationStatus.VERIFIED));

		assertThrows(InvalidAdminUserRoleChangeException.class,
			() -> service(users, new FakeBootstrapLock(users), new FakeAdminAuditLogs()).bootstrap());
	}

	private AdminBootstrapService service(
		FakeUsers users,
		AdminBootstrapLockRepository lock,
		FakeAdminAuditLogs auditLogs
	) {
		return new AdminBootstrapService(
			users,
			lock,
			normalizedEmail -> "v1:k:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA==",
			normalizedEmail -> new EmailSearchHash(ADMIN_HASH),
			new PlainPasswordHasher(),
			auditLogs,
			new AdminBootstrapProperties(ADMIN_EMAIL, "local-admin-password"),
			CLOCK);
	}

	private static User user(
		String emailSearchHash,
		UserRole role,
		UserStatus status,
		EmailVerificationStatus emailVerificationStatus
	) {
		return User.restoreFromPersistence(
			new UserId(UUID.randomUUID()),
			new EncryptedEmail("v1:k:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA=="),
			new EmailSearchHash(emailSearchHash),
			new PasswordHash("hash"),
			role,
			status,
			emailVerificationStatus,
			new EntityTimestamps(NOW.minusSeconds(60), NOW.minusSeconds(60)),
			0);
	}

	private static final class FakeBootstrapLock implements AdminBootstrapLockRepository {

		private final FakeUsers users;
		private boolean acquired;

		private FakeBootstrapLock(FakeUsers users) {
			this.users = users;
		}

		@Override
		public void acquireTransactionLock() {
			acquired = true;
			users.lockAcquired = true;
		}
	}

	private static final class FakeUsers implements UserRepository {

		private final Map<UserId, User> byId = new HashMap<>();
		private boolean lockAcquired;
		private boolean requireLockBeforeRoleCheck;

		private void put(User user) {
			byId.put(user.id(), user);
		}

		@Override
		public User save(User user) {
			if (existsByEmailSearchHash(user.emailSearchHash()) && !byId.containsKey(user.id())) {
				throw new DuplicateEmailException();
			}
			byId.put(user.id(), user);
			return user;
		}

		@Override
		public Optional<User> findById(UserId userId) {
			return Optional.ofNullable(byId.get(userId));
		}

		@Override
		public Optional<User> findByEmailSearchHash(EmailSearchHash emailSearchHash) {
			return byId.values().stream()
				.filter(user -> user.emailSearchHash().equals(emailSearchHash))
				.findFirst();
		}

		@Override
		public boolean existsByEmailSearchHash(EmailSearchHash emailSearchHash) {
			return findByEmailSearchHash(emailSearchHash).isPresent();
		}

		@Override
		public boolean existsByRole(UserRole role) {
			if (requireLockBeforeRoleCheck && !lockAcquired) {
				throw new AssertionError("관리자 존재 확인은 bootstrap lock 뒤에 실행되어야 합니다.");
			}
			return byId.values().stream().anyMatch(user -> user.role() == role);
		}
	}

	private static final class FakeAdminAuditLogs implements AdminAuditLogRepository {

		private final List<AdminAuditLogCommand> commands = new ArrayList<>();

		@Override
		public void save(AdminAuditLogCommand command) {
			commands.add(command);
		}
	}

	private static final class PlainPasswordHasher implements PasswordHasher {

		@Override
		public String hash(String password) {
			return "hashed:" + password;
		}

		@Override
		public boolean matches(String password, String passwordHash) {
			return passwordHash.equals(hash(password));
		}
	}
}
