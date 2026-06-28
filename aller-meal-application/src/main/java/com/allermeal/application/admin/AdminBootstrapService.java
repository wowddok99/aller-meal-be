package com.allermeal.application.admin;

import com.allermeal.application.auth.DuplicateEmailException;
import com.allermeal.application.auth.EmailVerificationRequester;
import com.allermeal.application.auth.InvalidSignupRequestException;
import com.allermeal.application.port.out.AdminAuditLogRepository;
import com.allermeal.application.port.out.AdminBootstrapLockRepository;
import com.allermeal.application.port.out.EmailEncryptor;
import com.allermeal.application.port.out.EmailSearchHasher;
import com.allermeal.application.port.out.PasswordHasher;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.command.AdminAuditLogCommand;
import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.EncryptedEmail;
import com.allermeal.domain.user.PasswordHash;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class AdminBootstrapService {

	private static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

	private final UserRepository userRepository;
	private final AdminBootstrapLockRepository bootstrapLockRepository;
	private final EmailEncryptor emailEncryptor;
	private final EmailSearchHasher emailSearchHasher;
	private final PasswordHasher passwordHasher;
	private final AdminAuditLogRepository auditLogRepository;
	private final AdminBootstrapProperties properties;
	private final Clock clock;

	public AdminBootstrapService(
		UserRepository userRepository,
		AdminBootstrapLockRepository bootstrapLockRepository,
		EmailEncryptor emailEncryptor,
		EmailSearchHasher emailSearchHasher,
		PasswordHasher passwordHasher,
		AdminAuditLogRepository auditLogRepository,
		AdminBootstrapProperties properties,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.bootstrapLockRepository = bootstrapLockRepository;
		this.emailEncryptor = emailEncryptor;
		this.emailSearchHasher = emailSearchHasher;
		this.passwordHasher = passwordHasher;
		this.auditLogRepository = auditLogRepository;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public Optional<AdminBootstrapResult> bootstrap() {
		if (!properties.enabled()) {
			return Optional.empty();
		}
		String email = EmailVerificationRequester.normalizeEmail(properties.email());
		String password = requirePassword(properties.password());
		EmailSearchHash emailSearchHash = emailSearchHasher.hash(email);
		Instant now = clock.instant();
		if (targetAlreadyAdmin(emailSearchHash)) {
			return userRepository.findByEmailSearchHash(emailSearchHash)
				.map(user -> new AdminBootstrapResult(false, user.id(), "BOOTSTRAP_ADMIN_ALREADY_READY"));
		}
		bootstrapLockRepository.acquireTransactionLock();
		Optional<User> existingUser = userRepository.findByEmailSearchHash(emailSearchHash);
		if (userRepository.existsByRole(UserRole.ADMIN)) {
			return Optional.of(new AdminBootstrapResult(false, null, "BOOTSTRAP_ADMIN_SKIPPED_EXISTING_ADMIN"));
		}
		if (existingUser.isPresent()) {
			return promoteExistingUser(existingUser.get(), now);
		}
		User admin = User.createAdmin(
			new UserId(UUID.randomUUID()),
			new EncryptedEmail(emailEncryptor.encrypt(email)),
			emailSearchHash,
			new PasswordHash(passwordHasher.hash(password)),
			now);
		try {
			User saved = userRepository.save(admin);
			auditLogRepository.save(audit(saved.id(), "BOOTSTRAP_ADMIN_CREATED", now));
			return Optional.of(new AdminBootstrapResult(true, saved.id(), "BOOTSTRAP_ADMIN_CREATED"));
		} catch (DuplicateEmailException exception) {
			throw exception;
		}
	}

	private Optional<AdminBootstrapResult> promoteExistingUser(User user, Instant now) {
		User next;
		try {
			next = user.promoteToAdmin(now).verifyEmail(now);
		} catch (IllegalStateException exception) {
			throw new InvalidAdminUserRoleChangeException();
		}
		if (next.role() == user.role() && next.emailVerificationStatus() == user.emailVerificationStatus()) {
			return Optional.of(new AdminBootstrapResult(false, user.id(), "BOOTSTRAP_ADMIN_ALREADY_READY"));
		}
		User saved = userRepository.save(next);
		auditLogRepository.save(audit(saved.id(), "BOOTSTRAP_ADMIN_PROMOTED", now));
		return Optional.of(new AdminBootstrapResult(true, saved.id(), "BOOTSTRAP_ADMIN_PROMOTED"));
	}

	private boolean targetAlreadyAdmin(EmailSearchHash emailSearchHash) {
		return userRepository.findByEmailSearchHash(emailSearchHash)
			.map(user -> user.role() == UserRole.ADMIN)
			.orElse(false);
	}

	private String requirePassword(String password) {
		if (password == null || password.length() < MIN_ADMIN_PASSWORD_LENGTH || password.isBlank()) {
			throw new InvalidSignupRequestException("관리자 bootstrap 비밀번호는 12자 이상이어야 합니다.");
		}
		return password;
	}

	private AdminAuditLogCommand audit(UserId targetUserId, String action, Instant createdAt) {
		return new AdminAuditLogCommand(
			UUID.randomUUID(),
			null,
			targetUserId,
			action,
			"SUCCEEDED",
			null,
			createdAt);
	}
}
