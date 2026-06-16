package com.allermeal.application.auth;

import com.allermeal.application.port.out.EmailEncryptor;
import com.allermeal.application.port.out.EmailSearchHasher;
import com.allermeal.application.port.out.PasswordHasher;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.EncryptedEmail;
import com.allermeal.domain.user.PasswordHash;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import java.time.Clock;
import java.util.UUID;

public final class SignupService {

	private static final int MIN_PASSWORD_LENGTH = 8;

	private final UserRepository userRepository;
	private final EmailEncryptor emailEncryptor;
	private final EmailSearchHasher emailSearchHasher;
	private final PasswordHasher passwordHasher;
	private final EmailVerificationRequester verificationRequester;
	private final Clock clock;

	public SignupService(
		UserRepository userRepository,
		EmailEncryptor emailEncryptor,
		EmailSearchHasher emailSearchHasher,
		PasswordHasher passwordHasher,
		EmailVerificationRequester verificationRequester,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.emailEncryptor = emailEncryptor;
		this.emailSearchHasher = emailSearchHasher;
		this.passwordHasher = passwordHasher;
		this.verificationRequester = verificationRequester;
		this.clock = clock;
	}

	public SignupResult signup(SignupCommand command) {
		String normalizedEmail = EmailVerificationRequester.normalizeEmail(command.email());
		String password = requirePassword(command.password());
		EmailSearchHash emailSearchHash = emailSearchHasher.hash(normalizedEmail);
		if (userRepository.existsByEmailSearchHash(emailSearchHash)) {
			throw new DuplicateEmailException();
		}
		User user = User.create(
			new UserId(UUID.randomUUID()),
			new EncryptedEmail(emailEncryptor.encrypt(normalizedEmail)),
			emailSearchHash,
			new PasswordHash(passwordHasher.hash(password)),
			clock.instant());
		User saved = userRepository.save(user);
		verificationRequester.sendVerificationMail(saved, normalizedEmail);
		return new SignupResult(saved.id(), saved.emailVerificationStatus());
	}

	private String requirePassword(String password) {
		if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
			throw new InvalidSignupRequestException("비밀번호는 8자 이상이어야 합니다.");
		}
		if (password.isBlank()) {
			throw new InvalidSignupRequestException("비밀번호는 비어 있을 수 없습니다.");
		}
		return password;
	}
}
