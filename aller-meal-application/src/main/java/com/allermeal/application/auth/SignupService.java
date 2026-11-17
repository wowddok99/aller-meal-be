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
		String displayName = normalizeDisplayName(command.displayName());
		String usageType = requireUsageType(command.usageType());
		EmailSearchHash emailSearchHash = emailSearchHasher.hash(normalizedEmail);
		if (userRepository.existsByEmailSearchHash(emailSearchHash)) {
			throw new DuplicateEmailException();
		}
		User user = User.create(
			new UserId(UUID.randomUUID()),
			new EncryptedEmail(emailEncryptor.encrypt(normalizedEmail)),
			emailSearchHash,
			new PasswordHash(passwordHasher.hash(password)),
			clock.instant()).withProfile(displayName, usageType);
		User saved = userRepository.save(user);
		verificationRequester.sendVerificationMail(saved, normalizedEmail);
		return new SignupResult(saved.id(), saved.emailVerificationStatus());
	}

	private String normalizeDisplayName(String input) {
		if (input == null) return null;
		int start = 0, end = input.length();
		while (start < end && isTrimSpace(input.charAt(start))) start++;
		while (end > start && isTrimSpace(input.charAt(end - 1))) end--;
		String name = input.substring(start, end);
		int length = name.codePointCount(0, name.length());
		if (length < 1 || length > 50) throw new InvalidSignupRequestException("표시 이름 길이가 올바르지 않습니다.");
		for (int i = 0; i < name.length();) {
			int cp = name.codePointAt(i);
			if (cp <= 0x1f || (cp >= 0x7f && cp <= 0x9f) || (cp >= 0xd800 && cp <= 0xdfff))
				throw new InvalidSignupRequestException("표시 이름 문자가 올바르지 않습니다.");
			i += Character.charCount(cp);
		}
		return name;
	}

	private boolean isTrimSpace(char c) {
		return (c >= 0x09 && c <= 0x0d) || c == 0x20 || c == 0xa0 || c == 0x1680
			|| (c >= 0x2000 && c <= 0x200a) || c == 0x2028 || c == 0x2029
			|| c == 0x202f || c == 0x205f || c == 0x3000 || c == 0xfeff;
	}

	private String requireUsageType(String usageType) {
		if (usageType != null && !usageType.equals("STUDENT") && !usageType.equals("PARENT"))
			throw new InvalidSignupRequestException("이용 대상이 올바르지 않습니다.");
		return usageType;
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
