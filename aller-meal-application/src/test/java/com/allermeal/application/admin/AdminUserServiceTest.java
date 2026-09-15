package com.allermeal.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allermeal.application.auth.DuplicateEmailException;
import com.allermeal.application.port.out.AdminAuditLogRepository;
import com.allermeal.application.port.out.AdminUserAccessAuditRepository;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.command.AdminAuditLogCommand;
import com.allermeal.application.port.out.command.AdminUserAccessAuditCommand;
import com.allermeal.application.port.out.result.AdminUserAccessAuditPageResult;
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

final class AdminUserServiceTest {

	private static final Instant NOW = Instant.parse("2026-06-28T09:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	@Test
	void rejectsNonAdminActor() {
		FakeUsers users = new FakeUsers();
		User actor = user("a".repeat(64), UserRole.MEMBER, UserStatus.ACTIVE);
		User target = user("b".repeat(64), UserRole.MEMBER, UserStatus.ACTIVE);
		users.put(target);

		assertThrows(AdminAuthorizationException.class,
			() -> service(users, new FakeAdminAuditLogs()).promoteToAdmin(actor, target.id(), command()));
	}

	@Test
	void promotesTargetAndWritesAuditLog() {
		FakeUsers users = new FakeUsers();
		User actor = user("a".repeat(64), UserRole.ADMIN, UserStatus.ACTIVE);
		User target = user("b".repeat(64), UserRole.MEMBER, UserStatus.ACTIVE);
		users.put(target);
		FakeAdminAuditLogs auditLogs = new FakeAdminAuditLogs();

		AdminUserRoleResult result = service(users, auditLogs).promoteToAdmin(actor, target.id(), command());

		assertEquals(UserRole.ADMIN, result.role());
		assertEquals(UserRole.ADMIN, users.findById(target.id()).orElseThrow().role());
		assertEquals(1, auditLogs.commands.size());
		assertEquals(actor.id(), auditLogs.commands.getFirst().actorUserId());
		assertEquals(target.id(), auditLogs.commands.getFirst().targetUserId());
		assertEquals("PROMOTE_USER_TO_ADMIN", auditLogs.commands.getFirst().action());
	}

	@Test
	void rejectsNonActiveTargetWithoutLeakingDomainException() {
		FakeUsers users = new FakeUsers();
		User actor = user("a".repeat(64), UserRole.ADMIN, UserStatus.ACTIVE);
		User target = user("b".repeat(64), UserRole.MEMBER, UserStatus.WITHDRAWAL_PENDING);
		users.put(target);

		assertThrows(AdminUserStateConflictException.class,
			() -> service(users, new FakeAdminAuditLogs()).promoteToAdmin(actor, target.id(), command()));
	}

	private static AdminUserService service(FakeUsers users, FakeAdminAuditLogs auditLogs) {
		return new AdminUserService(users, null, null, null, auditLogs, new FakeAccessAudits(), CLOCK);
	}

	private static AdminUserRoleChangeCommand command() {
		return new AdminUserRoleChangeCommand("운영 담당자 지정", 0L);
	}

	private static User user(String emailSearchHash, UserRole role, UserStatus status) {
		return User.restoreFromPersistence(
			new UserId(UUID.randomUUID()),
			new EncryptedEmail("v1:k:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA=="),
			new EmailSearchHash(emailSearchHash),
			new PasswordHash("hash"),
			role,
			status,
			EmailVerificationStatus.VERIFIED,
			new EntityTimestamps(NOW.minusSeconds(60), NOW.minusSeconds(60)),
			0);
	}

	private static final class FakeUsers implements UserRepository {

		private final Map<UserId, User> byId = new HashMap<>();

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

	private static final class FakeAccessAudits implements AdminUserAccessAuditRepository {

		@Override
		public void save(AdminUserAccessAuditCommand command) {
		}

		@Override
		public AdminUserAccessAuditPageResult findByTargetUserId(UserId targetUserId, int page, int pageSize) {
			return new AdminUserAccessAuditPageResult(List.of(), page, pageSize, 0);
		}
	}
}
