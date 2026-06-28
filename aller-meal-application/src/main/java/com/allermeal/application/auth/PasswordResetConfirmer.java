package com.allermeal.application.auth;

import com.allermeal.application.port.out.EmailVerificationTokenHasher;
import com.allermeal.application.port.out.PasswordHasher;
import com.allermeal.application.port.out.PasswordResetTokenStore;
import com.allermeal.application.port.out.RefreshTokenStore;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.domain.user.PasswordHash;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

public final class PasswordResetConfirmer {

	private static final int MIN_PASSWORD_LENGTH = 8;

	private final UserRepository userRepository;
	private final PasswordHasher passwordHasher;
	private final EmailVerificationTokenHasher tokenHasher;
	private final PasswordResetTokenStore tokenStore;
	private final RefreshTokenStore refreshTokenStore;
	private final Duration refreshTokenTtl;
	private final Clock clock;

	public PasswordResetConfirmer(
		UserRepository userRepository,
		PasswordHasher passwordHasher,
		EmailVerificationTokenHasher tokenHasher,
		PasswordResetTokenStore tokenStore,
		RefreshTokenStore refreshTokenStore,
		Duration refreshTokenTtl,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.passwordHasher = passwordHasher;
		this.tokenHasher = tokenHasher;
		this.tokenStore = tokenStore;
		this.refreshTokenStore = refreshTokenStore;
		this.refreshTokenTtl = refreshTokenTtl;
		this.clock = clock;
	}

	public void confirm(PasswordResetConfirmCommand command) {
		String tokenHash = tokenHasher.hash(requireToken(command.token()));
		String password = requirePassword(command.password());
		UserId userId = tokenStore.consume(tokenHash).orElseThrow(InvalidPasswordResetTokenException::new);
		User user = userRepository.findById(userId).orElseThrow(InvalidPasswordResetTokenException::new);
		try {
			userRepository.save(user.changePassword(new PasswordHash(passwordHasher.hash(password)), clock.instant()));
		} catch (IllegalStateException exception) {
			throw new InvalidPasswordResetTokenException();
		}
		refreshTokenStore.revokeAll(userId, refreshTokenTtl);
	}

	private String requireToken(String token) {
		String requiredToken = Objects.requireNonNullElse(token, "").trim();
		if (requiredToken.isBlank() || requiredToken.length() > 512) {
			throw new InvalidPasswordResetTokenException();
		}
		return requiredToken;
	}

	private String requirePassword(String password) {
		if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.isBlank()) {
			throw new InvalidSignupRequestException("비밀번호는 8자 이상이며 비어 있을 수 없습니다.");
		}
		return password;
	}
}
