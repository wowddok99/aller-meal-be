package com.allermeal.application.auth;

import com.allermeal.application.port.out.AccessTokenIssuer;
import com.allermeal.application.port.out.EmailVerificationTokenHasher;
import com.allermeal.application.port.out.RefreshTokenStore;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.VerificationTokenGenerator;
import com.allermeal.application.port.out.command.RotateRefreshTokenCommand;
import com.allermeal.application.port.out.result.RefreshTokenRotationResult;
import com.allermeal.application.port.out.result.RefreshTokenRotationStatus;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class RefreshService {

	private final UserRepository userRepository;
	private final AccessTokenIssuer accessTokenIssuer;
	private final VerificationTokenGenerator refreshTokenGenerator;
	private final EmailVerificationTokenHasher tokenHasher;
	private final RefreshTokenStore refreshTokenStore;
	private final Duration accessTokenTtl;
	private final Duration refreshTokenTtl;
	private final Clock clock;

	public RefreshService(
		UserRepository userRepository,
		AccessTokenIssuer accessTokenIssuer,
		VerificationTokenGenerator refreshTokenGenerator,
		EmailVerificationTokenHasher tokenHasher,
		RefreshTokenStore refreshTokenStore,
		Duration accessTokenTtl,
		Duration refreshTokenTtl,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.accessTokenIssuer = accessTokenIssuer;
		this.refreshTokenGenerator = refreshTokenGenerator;
		this.tokenHasher = tokenHasher;
		this.refreshTokenStore = refreshTokenStore;
		this.accessTokenTtl = accessTokenTtl;
		this.refreshTokenTtl = refreshTokenTtl;
		this.clock = clock;
	}

	public AuthenticationResult refresh(String refreshToken) {
		String oldTokenHash = tokenHasher.hash(requireRefreshToken(refreshToken));
		String newRefreshToken = refreshTokenGenerator.generate();
		String newTokenHash = tokenHasher.hash(newRefreshToken);
		Instant issuedAt = clock.instant();
		Instant refreshExpiresAt = issuedAt.plus(refreshTokenTtl);
		RefreshTokenRotationResult rotation = refreshTokenStore.rotate(new RotateRefreshTokenCommand(
			oldTokenHash, newTokenHash, refreshExpiresAt, refreshTokenTtl));
		if (rotation.status() != RefreshTokenRotationStatus.ROTATED) {
			throw new UnauthorizedAccessException();
		}
		User user = userRepository.findById(rotation.userId()).orElseThrow(UnauthorizedAccessException::new);
		if (user.status() != UserStatus.ACTIVE || user.emailVerificationStatus() != EmailVerificationStatus.VERIFIED) {
			refreshTokenStore.revoke(newTokenHash);
			throw new UnauthorizedAccessException();
		}
		Instant accessExpiresAt = issuedAt.plus(accessTokenTtl);
		String accessToken = accessTokenIssuer.issue(user, accessTokenTtl);
		return new AuthenticationResult(
			user.id(),
			user.emailVerificationStatus(),
			accessToken,
			accessExpiresAt,
			newRefreshToken,
			refreshExpiresAt);
	}

	public void logout(String refreshToken) {
		String requiredRefreshToken = Objects.requireNonNullElse(refreshToken, "").trim();
		if (!requiredRefreshToken.isBlank()) {
			refreshTokenStore.revoke(tokenHasher.hash(requiredRefreshToken));
		}
	}

	private String requireRefreshToken(String refreshToken) {
		String requiredRefreshToken = Objects.requireNonNullElse(refreshToken, "").trim();
		if (requiredRefreshToken.isBlank() || requiredRefreshToken.length() > 512) {
			throw new UnauthorizedAccessException();
		}
		return requiredRefreshToken;
	}
}
