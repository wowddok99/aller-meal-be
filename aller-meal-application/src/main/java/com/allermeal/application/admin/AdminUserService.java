package com.allermeal.application.admin;

import com.allermeal.application.port.out.AdminAuditLogRepository;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.command.AdminAuditLogCommand;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class AdminUserService {

	private final UserRepository userRepository;
	private final AdminAuditLogRepository auditLogRepository;
	private final Clock clock;

	public AdminUserService(
		UserRepository userRepository,
		AdminAuditLogRepository auditLogRepository,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.auditLogRepository = auditLogRepository;
		this.clock = clock;
	}

	@Transactional
	public AdminUserRoleResult promoteToAdmin(User actor, UserId targetUserId) {
		requireAdmin(actor);
		User target = userRepository.findById(targetUserId)
			.orElseThrow(AdminUserNotFoundException::new);
		Instant changedAt = clock.instant();
		User next;
		try {
			next = target.promoteToAdmin(changedAt);
		} catch (IllegalStateException exception) {
			throw new InvalidAdminUserRoleChangeException();
		}
		User saved = userRepository.save(next);
		auditLogRepository.save(new AdminAuditLogCommand(
			UUID.randomUUID(),
			actor.id(),
			saved.id(),
			"PROMOTE_USER_TO_ADMIN",
			"SUCCEEDED",
			null,
			changedAt));
		return new AdminUserRoleResult(saved.id(), saved.role());
	}

	private void requireAdmin(User actor) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new AdminAuthorizationException();
		}
	}
}
