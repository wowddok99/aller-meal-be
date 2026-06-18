package com.allermeal.application.auth;

import com.allermeal.application.port.out.AccessTokenIssuer;
import com.allermeal.application.port.out.EmailSearchHasher;
import com.allermeal.application.port.out.EmailVerificationTokenHasher;
import com.allermeal.application.port.out.PasswordHasher;
import com.allermeal.application.port.out.RefreshTokenStore;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.VerificationTokenGenerator;
import com.allermeal.application.port.out.command.RefreshTokenCommand;
import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public final class LoginService {

	private final UserRepository userRepository;
	private final EmailSearchHasher emailSearchHasher;
	private final PasswordHasher passwordHasher;
	private final AccessTokenIssuer accessTokenIssuer;
	private final VerificationTokenGenerator refreshTokenGenerator;
	private final EmailVerificationTokenHasher tokenHasher;
	private final RefreshTokenStore refreshTokenStore;
	private final Duration accessTokenTtl;
	private final Duration refreshTokenTtl;
	private final Clock clock;

	public LoginService(
		UserRepository userRepository,
		EmailSearchHasher emailSearchHasher,
		PasswordHasher passwordHasher,
		AccessTokenIssuer accessTokenIssuer,
		VerificationTokenGenerator refreshTokenGenerator,
		EmailVerificationTokenHasher tokenHasher,
		RefreshTokenStore refreshTokenStore,
		Duration accessTokenTtl,
		Duration refreshTokenTtl,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.emailSearchHasher = emailSearchHasher;
		this.passwordHasher = passwordHasher;
		this.accessTokenIssuer = accessTokenIssuer;
		this.refreshTokenGenerator = refreshTokenGenerator;
		this.tokenHasher = tokenHasher;
		this.refreshTokenStore = refreshTokenStore;
		this.accessTokenTtl = accessTokenTtl;
		this.refreshTokenTtl = refreshTokenTtl;
		this.clock = clock;
	}

	public AuthenticationResult login(LoginCommand command) {
		String normalizedEmail = EmailVerificationRequester.normalizeEmail(command.email());
		String password = requirePassword(command.password());
		EmailSearchHash emailSearchHash = emailSearchHasher.hash(normalizedEmail);
		User user = userRepository.findByEmailSearchHash(emailSearchHash)
			.orElseThrow(InvalidLoginCredentialsException::new);
		if (!passwordHasher.matches(password, user.passwordHash().value())) {
			throw new InvalidLoginCredentialsException();
		}
		if (user.status() != UserStatus.ACTIVE) {
			throw new InvalidLoginCredentialsException();
		}
		if (user.emailVerificationStatus() != EmailVerificationStatus.VERIFIED) {
			throw new EmailNotVerifiedException();
		}
		Instant issuedAt = clock.instant();
		Instant accessExpiresAt = issuedAt.plus(accessTokenTtl);
		Instant refreshExpiresAt = issuedAt.plus(refreshTokenTtl);
		String accessToken = accessTokenIssuer.issue(user, accessTokenTtl);
		String refreshToken = refreshTokenGenerator.generate();
		refreshTokenStore.store(new RefreshTokenCommand(
			user.id(), tokenHasher.hash(refreshToken), refreshExpiresAt, refreshTokenTtl));
		return new AuthenticationResult(
			user.id(),
			user.emailVerificationStatus(),
			accessToken,
			accessExpiresAt,
			refreshToken,
			refreshExpiresAt);
	}

	private String requirePassword(String password) {
		if (password == null || password.isBlank()) {
			throw new InvalidLoginCredentialsException();
		}
		return password;
	}
}
