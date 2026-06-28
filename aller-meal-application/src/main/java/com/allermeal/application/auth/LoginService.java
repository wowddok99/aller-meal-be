package com.allermeal.application.auth;

import com.allermeal.application.port.out.AccessTokenIssuer;
import com.allermeal.application.port.out.EmailSearchHasher;
import com.allermeal.application.port.out.EmailVerificationTokenHasher;
import com.allermeal.application.port.out.PasswordHasher;
import com.allermeal.application.port.out.LoginAttemptStore;
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
	private final LoginAttemptStore loginAttemptStore;
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
		LoginAttemptStore loginAttemptStore,
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
		this.loginAttemptStore = loginAttemptStore;
		this.accessTokenTtl = accessTokenTtl;
		this.refreshTokenTtl = refreshTokenTtl;
		this.clock = clock;
	}

	public AuthenticationResult login(LoginCommand command) {
		String normalizedEmail = EmailVerificationRequester.normalizeEmail(command.email());
		EmailSearchHash emailSearchHash = emailSearchHasher.hash(normalizedEmail);
		if (loginAttemptStore.isLocked(emailSearchHash.value())) {
			throw new LoginTemporarilyLockedException();
		}
		String password = command.password();
		if (password == null || password.isBlank()) {
			throw invalidCredentials(emailSearchHash.value());
		}
		User user = userRepository.findByEmailSearchHash(emailSearchHash)
			.orElseThrow(() -> invalidCredentials(emailSearchHash.value()));
		if (!passwordHasher.matches(password, user.passwordHash().value())) {
			throw invalidCredentials(emailSearchHash.value());
		}
		if (user.status() != UserStatus.ACTIVE) {
			throw new InvalidLoginCredentialsException();
		}
		if (user.emailVerificationStatus() != EmailVerificationStatus.VERIFIED) {
			throw new EmailNotVerifiedException();
		}
		if (!loginAttemptStore.clearIfUnlocked(emailSearchHash.value())) {
			throw new LoginTemporarilyLockedException();
		}
		Instant issuedAt = clock.instant();
		Instant accessExpiresAt = issuedAt.plus(accessTokenTtl);
		Instant refreshExpiresAt = issuedAt.plus(refreshTokenTtl);
		String accessToken = accessTokenIssuer.issue(user, accessTokenTtl);
		String refreshTokenFamilyId = refreshTokenGenerator.generate();
		String refreshToken = refreshTokenGenerator.generate();
		refreshTokenStore.store(new RefreshTokenCommand(
			user.id(),
			tokenHasher.hash(refreshTokenFamilyId),
			tokenHasher.hash(refreshToken),
			issuedAt,
			refreshExpiresAt,
			refreshTokenTtl));
		return new AuthenticationResult(
			user.id(),
			user.emailVerificationStatus(),
			accessToken,
			accessExpiresAt,
			refreshToken,
			refreshExpiresAt);
	}

	private InvalidLoginCredentialsException invalidCredentials(String emailHash) {
		loginAttemptStore.recordFailure(emailHash);
		return new InvalidLoginCredentialsException();
	}

}
