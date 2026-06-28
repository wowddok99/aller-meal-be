package com.allermeal.application.auth;

import com.allermeal.application.port.out.EmailSearchHasher;
import com.allermeal.application.port.out.EmailVerificationTokenHasher;
import com.allermeal.application.port.out.PasswordResetMailSender;
import com.allermeal.application.port.out.PasswordResetTokenStore;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.VerificationTokenGenerator;
import com.allermeal.application.port.out.command.PasswordResetMailCommand;
import com.allermeal.application.port.out.command.PasswordResetTokenCommand;
import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserStatus;
import java.time.Duration;

public final class PasswordResetRequester {

	private final UserRepository userRepository;
	private final EmailSearchHasher emailSearchHasher;
	private final VerificationTokenGenerator tokenGenerator;
	private final EmailVerificationTokenHasher tokenHasher;
	private final PasswordResetTokenStore tokenStore;
	private final PasswordResetMailSender mailSender;
	private final Duration tokenTtl;

	public PasswordResetRequester(
		UserRepository userRepository,
		EmailSearchHasher emailSearchHasher,
		VerificationTokenGenerator tokenGenerator,
		EmailVerificationTokenHasher tokenHasher,
		PasswordResetTokenStore tokenStore,
		PasswordResetMailSender mailSender,
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

	public void request(PasswordResetRequestCommand command) {
		String normalizedEmail = EmailVerificationRequester.normalizeEmail(command.email());
		EmailSearchHash emailSearchHash = emailSearchHasher.hash(normalizedEmail);
		User user = userRepository.findByEmailSearchHash(emailSearchHash).orElse(null);
		if (user == null || user.status() != UserStatus.ACTIVE
			|| user.emailVerificationStatus() != EmailVerificationStatus.VERIFIED) {
			return;
		}
		String token = tokenGenerator.generate();
		tokenStore.store(new PasswordResetTokenCommand(user.id(), tokenHasher.hash(token), tokenTtl));
		mailSender.send(new PasswordResetMailCommand(normalizedEmail, token));
	}
}
