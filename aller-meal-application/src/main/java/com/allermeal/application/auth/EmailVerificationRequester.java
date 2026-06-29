package com.allermeal.application.auth;

import com.allermeal.application.port.out.EmailSearchHasher;
import com.allermeal.application.port.out.EmailVerificationMailSender;
import com.allermeal.application.port.out.EmailVerificationTokenHasher;
import com.allermeal.application.port.out.EmailVerificationTokenStore;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.VerificationTokenGenerator;
import com.allermeal.application.port.out.command.EmailVerificationMailCommand;
import com.allermeal.application.port.out.command.EmailVerificationTokenCommand;
import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.User;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

public final class EmailVerificationRequester {

	private static final Pattern EMAIL_PATTERN = Pattern.compile(
		"^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
			+ "(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");

	private final UserRepository userRepository;
	private final EmailSearchHasher emailSearchHasher;
	private final VerificationTokenGenerator tokenGenerator;
	private final EmailVerificationTokenHasher tokenHasher;
	private final EmailVerificationTokenStore tokenStore;
	private final EmailVerificationMailSender mailSender;
	private final Duration tokenTtl;

	public EmailVerificationRequester(
		UserRepository userRepository,
		EmailSearchHasher emailSearchHasher,
		VerificationTokenGenerator tokenGenerator,
		EmailVerificationTokenHasher tokenHasher,
		EmailVerificationTokenStore tokenStore,
		EmailVerificationMailSender mailSender,
		Duration tokenTtl
	) {
		this.userRepository = userRepository;
		this.emailSearchHasher = emailSearchHasher;
		this.tokenGenerator = tokenGenerator;
		this.tokenHasher = tokenHasher;
		this.tokenStore = tokenStore;
		this.mailSender = mailSender;
		this.tokenTtl = tokenTtl;
	}

	public EmailVerificationRequestResult request(EmailVerificationRequestCommand command) {
		String normalizedEmail = normalizeEmail(command.email());
		EmailSearchHash emailSearchHash = emailSearchHasher.hash(normalizedEmail);
		User user = userRepository.findByEmailSearchHash(emailSearchHash)
			.orElseThrow(UserEmailNotFoundException::new);
		if (user.emailVerificationStatus() == EmailVerificationStatus.VERIFIED) {
			throw new EmailAlreadyVerifiedException();
		}
		sendVerificationMail(user, normalizedEmail);
		return new EmailVerificationRequestResult(user.emailVerificationStatus());
	}

	void sendVerificationMail(User user, String normalizedEmail) {
		String token = tokenGenerator.generate();
		String tokenHash = tokenHasher.hash(token);
		tokenStore.store(new EmailVerificationTokenCommand(user.id(), tokenHash, tokenTtl));
		mailSender.send(new EmailVerificationMailCommand(normalizedEmail, token));
	}

	public static String normalizeEmail(String email) {
		if (email == null) {
			throw new InvalidSignupRequestException("이메일은 필수입니다.");
		}
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank() || normalized.length() > 320 || !EMAIL_PATTERN.matcher(normalized).matches()) {
			throw new InvalidSignupRequestException("이메일 형식이 올바르지 않습니다.");
		}
		return normalized;
	}
}
