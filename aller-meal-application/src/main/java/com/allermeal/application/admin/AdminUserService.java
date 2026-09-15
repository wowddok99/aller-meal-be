package com.allermeal.application.admin;

import com.allermeal.application.port.out.AdminAuditLogRepository;
import com.allermeal.application.port.out.AdminUserAccessAuditRepository;
import com.allermeal.application.port.out.AdminUserQueryRepository;
import com.allermeal.application.port.out.EmailDecryptor;
import com.allermeal.application.port.out.EmailSearchHasher;
import com.allermeal.application.port.out.NotificationRequestRepository;
import com.allermeal.application.port.out.RefreshTokenStore;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.command.AdminAuditLogCommand;
import com.allermeal.application.port.out.command.AdminUserAccessAuditCommand;
import com.allermeal.application.port.out.result.AdminUserQueryResult;
import com.allermeal.application.auth.EmailVerificationRequester;
import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

public class AdminUserService {

	private final UserRepository userRepository;
	private final AdminUserQueryRepository adminUserQueryRepository;
	private final EmailDecryptor emailDecryptor;
	private final EmailSearchHasher emailSearchHasher;
	private final AdminAuditLogRepository auditLogRepository;
	private final AdminUserAccessAuditRepository accessAuditRepository;
	private final RefreshTokenStore refreshTokenStore;
	private final NotificationRequestRepository notificationRequestRepository;
	private final Duration refreshTokenTtl;
	private final Clock clock;

	AdminUserService(
		UserRepository userRepository,
		AdminUserQueryRepository adminUserQueryRepository,
		EmailDecryptor emailDecryptor,
		EmailSearchHasher emailSearchHasher,
		AdminAuditLogRepository auditLogRepository,
		AdminUserAccessAuditRepository accessAuditRepository,
		Clock clock
	) {
		this(
			Objects.requireNonNull(userRepository),
			requireOrUnavailable(adminUserQueryRepository, AdminUserQueryRepository.class),
			requireOrUnavailable(emailDecryptor, EmailDecryptor.class),
			requireOrUnavailable(emailSearchHasher, EmailSearchHasher.class),
			Objects.requireNonNull(auditLogRepository),
			Objects.requireNonNull(accessAuditRepository),
			unavailable(RefreshTokenStore.class),
			unavailable(NotificationRequestRepository.class),
			Duration.ZERO,
			Objects.requireNonNull(clock));
	}

	public AdminUserService(
		UserRepository userRepository,
		AdminUserQueryRepository adminUserQueryRepository,
		EmailDecryptor emailDecryptor,
		EmailSearchHasher emailSearchHasher,
		AdminAuditLogRepository auditLogRepository,
		AdminUserAccessAuditRepository accessAuditRepository,
		RefreshTokenStore refreshTokenStore,
		NotificationRequestRepository notificationRequestRepository,
		Duration refreshTokenTtl,
		Clock clock
	) {
		this.userRepository = Objects.requireNonNull(userRepository);
		this.adminUserQueryRepository = Objects.requireNonNull(adminUserQueryRepository);
		this.emailDecryptor = Objects.requireNonNull(emailDecryptor);
		this.emailSearchHasher = Objects.requireNonNull(emailSearchHasher);
		this.auditLogRepository = Objects.requireNonNull(auditLogRepository);
		this.accessAuditRepository = Objects.requireNonNull(accessAuditRepository);
		this.refreshTokenStore = Objects.requireNonNull(refreshTokenStore);
		this.notificationRequestRepository = Objects.requireNonNull(notificationRequestRepository);
		this.refreshTokenTtl = Objects.requireNonNull(refreshTokenTtl);
		this.clock = Objects.requireNonNull(clock);
	}

	public AdminUserPageResult findUsers(User actor, String query, String status, int page, int pageSize) {
		requireAdmin(actor);
		validatePage(page, pageSize);
		UserStatus normalizedStatus = parseStatus(status);
		UserId userId = null;
		EmailSearchHash emailSearchHash = null;
		if (query != null) {
			if (query.isBlank()) throw new AdminInvalidUserQueryRequestException();
			String normalizedQuery = query.trim();
			try {
				userId = new UserId(UUID.fromString(normalizedQuery));
			} catch (IllegalArgumentException ignored) {
				emailSearchHash = emailSearchHasher.hash(normalizeEmail(normalizedQuery));
			}
		}
		var result = adminUserQueryRepository.findVisibleUsers(
			normalizedStatus, userId, emailSearchHash, page, pageSize);
		return new AdminUserPageResult(
			result.items().stream().map(this::toListItem).toList(),
			result.page(), result.pageSize(), result.totalCount());
	}

	public AdminUserDetailResult findUser(User actor, UserId targetUserId) {
		requireAdmin(actor);
		AdminUserQueryResult result = adminUserQueryRepository.findVisibleUserById(targetUserId)
			.orElseThrow(AdminUserNotFoundException::new);
		return new AdminUserDetailResult(
			result.userId(),
			emailDecryptor.decrypt(result.encryptedEmail()),
			result.role(),
			result.status(),
			result.emailVerificationStatus(),
			result.createdAt(),
			result.withdrawalDueAt(),
			result.version(),
			availableActions(result));
	}

	public AdminUserAccessHistoryPageResult findAccessHistory(
		User actor,
		UserId targetUserId,
		int page,
		int pageSize
	) {
		requireAdmin(actor);
		validatePage(page, pageSize);
		adminUserQueryRepository.findVisibleUserById(targetUserId)
			.orElseThrow(AdminUserNotFoundException::new);
		var result = accessAuditRepository.findByTargetUserId(targetUserId, page, pageSize);
		return new AdminUserAccessHistoryPageResult(
			result.items().stream().map(item -> new AdminUserAccessHistoryItemResult(
				item.eventId(), item.actorUserId(), item.action(), item.beforeRole(), item.afterRole(),
				item.beforeStatus(), item.afterStatus(), item.reason(), item.createdAt(), item.source())).toList(),
			result.page(), result.pageSize(), result.totalCount());
	}

	@Transactional
	public AdminUserRoleResult promoteToAdmin(
		User actor,
		UserId targetUserId,
		AdminUserRoleChangeCommand command
	) {
		requireAdmin(actor);
		User target = userRepository.findById(targetUserId)
			.orElseThrow(AdminUserNotFoundException::new);
		if (target.status() == UserStatus.DISABLED) {
			throw new AdminUserNotFoundException();
		}
		if (!command.expectedVersion().equals(target.version())) {
			throw new AdminUserStateConflictException();
		}
		Instant changedAt = clock.instant();
		User next;
		try {
			next = target.promoteToAdmin(changedAt);
		} catch (IllegalStateException exception) {
			throw new AdminUserStateConflictException();
		}
		User saved;
		try {
			saved = userRepository.save(next);
		} catch (OptimisticLockingFailureException exception) {
			throw new AdminUserStateConflictException();
		}
		UUID accessAuditEventId = UUID.randomUUID();
		accessAuditRepository.save(new AdminUserAccessAuditCommand(
			accessAuditEventId,
			actor.id(),
			saved.id(),
			"PROMOTE_USER_TO_ADMIN",
			target.role(),
			saved.role(),
			target.status(),
			saved.status(),
			command.reason(),
			changedAt));
		auditLogRepository.save(new AdminAuditLogCommand(
			UUID.randomUUID(),
			actor.id(),
			saved.id(),
			"PROMOTE_USER_TO_ADMIN",
			"SUCCEEDED",
			"USER_ACCESS_AUDIT_EVENT:" + accessAuditEventId,
			changedAt));
		return new AdminUserRoleResult(
			saved.id(), saved.role(), saved.status(), saved.version(), "PROMOTE_USER_TO_ADMIN", changedAt);
	}

	@Transactional
	public AdminUserRoleResult changeSuspension(
		User actor,
		UserId targetUserId,
		AdminUserSuspensionCommand command
	) {
		requireAdmin(actor);
		User target = userRepository.findById(targetUserId)
			.orElseThrow(AdminUserNotFoundException::new);
		if (target.status() == UserStatus.DISABLED) {
			throw new AdminUserNotFoundException();
		}
		if (!command.expectedVersion().equals(target.version())) {
			throw new AdminUserStateConflictException();
		}
		Instant changedAt = clock.instant();
		User next;
		try {
			next = switch (command.action()) {
				case SUSPEND -> target.suspend(changedAt);
				case UNSUSPEND -> target.unsuspend(changedAt);
			};
		} catch (IllegalStateException exception) {
			throw new AdminUserStateConflictException();
		}

		// Redis failure must prevent the SUSPENDED row from being saved. DB failure after this point is fail-safe.
		// Revoke again on release so no pre-release Refresh Token can survive an inconsistent legacy state.
		refreshTokenStore.revokeAll(target.id(), refreshTokenTtl);

		User saved;
		try {
			saved = userRepository.save(next);
		} catch (OptimisticLockingFailureException exception) {
			throw new AdminUserStateConflictException();
		}
		if (command.action() == AdminUserSuspensionAction.SUSPEND) {
			notificationRequestRepository.cancelPendingAndRetryForSuspendedOwner(saved.id(), changedAt);
		}
		String action = command.action() == AdminUserSuspensionAction.SUSPEND ? "SUSPEND_USER" : "UNSUSPEND_USER";
		UUID accessAuditEventId = UUID.randomUUID();
		accessAuditRepository.save(new AdminUserAccessAuditCommand(
			accessAuditEventId,
			actor.id(),
			saved.id(),
			action,
			target.role(),
			saved.role(),
			target.status(),
			saved.status(),
			command.reason(),
			changedAt));
		auditLogRepository.save(new AdminAuditLogCommand(
			UUID.randomUUID(),
			actor.id(),
			saved.id(),
			action,
			"SUCCEEDED",
			"USER_ACCESS_AUDIT_EVENT:" + accessAuditEventId,
			changedAt));
		return new AdminUserRoleResult(saved.id(), saved.role(), saved.status(), saved.version(), action, changedAt);
	}

	private void requireAdmin(User actor) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new AdminAuthorizationException();
		}
	}

	private AdminUserListItemResult toListItem(AdminUserQueryResult result) {
		return new AdminUserListItemResult(
			result.userId(), emailDecryptor.decrypt(result.encryptedEmail()), result.role(), result.status(),
			result.emailVerificationStatus(), result.createdAt());
	}

	private AdminUserAvailableActions availableActions(AdminUserQueryResult result) {
		boolean mutableMember = result.role() == UserRole.MEMBER;
		return new AdminUserAvailableActions(
			mutableMember && result.status() == UserStatus.ACTIVE
				&& result.emailVerificationStatus() == EmailVerificationStatus.VERIFIED,
			mutableMember && result.status() == UserStatus.ACTIVE,
			mutableMember && result.status() == UserStatus.SUSPENDED);
	}

	private void validatePage(int page, int pageSize) {
		if (page < 1 || pageSize < 1 || pageSize > 100
			|| (long) page > ((long) Integer.MAX_VALUE / pageSize) + 1) {
			throw new AdminInvalidUserQueryRequestException();
		}
	}

	private UserStatus parseStatus(String status) {
		if (status == null) return null;
		if (status.isBlank()) throw new AdminInvalidUserQueryRequestException();
		try {
			UserStatus parsed = UserStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
			if (parsed == UserStatus.DISABLED) throw new AdminInvalidUserQueryRequestException();
			return parsed;
		} catch (IllegalArgumentException exception) {
			throw new AdminInvalidUserQueryRequestException();
		}
	}

	private String normalizeEmail(String query) {
		try {
			return EmailVerificationRequester.normalizeEmail(query);
		} catch (RuntimeException exception) {
			throw new AdminInvalidUserQueryRequestException();
		}
	}

	private static <T> T requireOrUnavailable(T dependency, Class<T> type) {
		return dependency == null ? unavailable(type) : dependency;
	}

	private static <T> T unavailable(Class<T> type) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
			(proxy, method, arguments) -> {
				throw new IllegalStateException("관리자 사용자 서비스 의존성이 설정되지 않았습니다: " + type.getSimpleName());
			}));
	}
}
