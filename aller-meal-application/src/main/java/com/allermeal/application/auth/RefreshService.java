package com.allermeal.application.auth;

import com.allermeal.application.port.out.AccessTokenIssuer;
import com.allermeal.application.port.out.EmailVerificationTokenHasher;
import com.allermeal.application.port.out.RefreshTokenStore;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.VerificationTokenGenerator;
import com.allermeal.application.port.out.command.RotateRefreshTokenCommand;
import com.allermeal.application.port.out.result.RefreshTokenRotationResult;
import com.allermeal.application.port.out.result.RefreshTokenRotationStatus;
import com.allermeal.application.port.out.result.RefreshSessionLookupResult;
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
		return refresh(refreshToken, null);
	}

	public AuthenticationResult execute(String refreshToken, String operationId) {
		return refresh(refreshToken, requireOperationId(operationId));
	}

	private AuthenticationResult refresh(String refreshToken, String operationId) {
		String oldTokenHash = tokenHasher.hash(requireRefreshToken(refreshToken));
		String newRefreshToken = refreshTokenGenerator.generate();
		String newTokenHash = tokenHasher.hash(newRefreshToken);
		Instant issuedAt = clock.instant();
		Instant refreshExpiresAt = issuedAt.plus(refreshTokenTtl);
		RefreshTokenRotationResult rotation = refreshTokenStore.rotate(new RotateRefreshTokenCommand(
			oldTokenHash, newTokenHash, refreshExpiresAt, refreshTokenTtl, issuedAt, RefreshSessionPolicy.SLIDING_TTL, operationId));
		if (rotation.status() == RefreshTokenRotationStatus.OPERATION_EXPIRED) {
			throw new RefreshOperationExpiredException();
		}
		if (rotation.status() != RefreshTokenRotationStatus.ROTATED) {
			throw new UnauthorizedAccessException();
		}
		User user = userRepository.findById(rotation.userId()).orElseThrow(UnauthorizedAccessException::new);
		if (rotation.sessionVersion() == null
			|| rotation.sessionVersion() != user.sessionVersion()
			|| !canRefresh(user, issuedAt)
			|| user.emailVerificationStatus() != EmailVerificationStatus.VERIFIED) {
			refreshTokenStore.revoke(newTokenHash);
			throw new UnauthorizedAccessException();
		}
		if (rotation.refreshExpiresAt() != null) {
			refreshExpiresAt = rotation.refreshExpiresAt();
		}
		Duration effectiveAccessTtl = accessTokenTtl;
		if (rotation.absoluteExpiresAt() != null) {
			Duration remaining = Duration.between(issuedAt, rotation.absoluteExpiresAt());
			if (remaining.compareTo(effectiveAccessTtl) < 0) {
				effectiveAccessTtl = remaining;
			}
		}
		Instant accessExpiresAt = issuedAt.plus(effectiveAccessTtl);
		String sessionId = tokenHasher.hash(rotation.familyId());
		String accessToken = accessTokenIssuer.issueUntil(user, accessExpiresAt, sessionId);
		return new AuthenticationResult(
			user.id(),
			user.emailVerificationStatus(),
			accessToken,
			accessExpiresAt,
			newRefreshToken,
			refreshExpiresAt,
			rotation.rememberMe(),
			sessionId);
	}

	public RefreshSessionStatusResult sessionStatus(String refreshToken) {
		RefreshSessionLookupResult session = refreshTokenStore.lookup(tokenHasher.hash(requireRefreshToken(refreshToken)));
		if (session == null) throw new UnauthorizedAccessException();
		User user = userRepository.findById(session.userId()).orElseThrow(UnauthorizedAccessException::new);
		if (session.sessionVersion() != user.sessionVersion() || !canRefresh(user, clock.instant())
			|| user.emailVerificationStatus() != EmailVerificationStatus.VERIFIED) {
			throw new UnauthorizedAccessException();
		}
		return new RefreshSessionStatusResult(tokenHasher.hash(session.familyId()), user.id().value());
	}

	public RefreshOperationResult reserve(String refreshToken) {
		RefreshSessionStatusResult session = sessionStatus(refreshToken);
		String operationId = refreshTokenGenerator.generate();
		var result = refreshTokenStore.reserve(tokenHasher.hash(requireRefreshToken(refreshToken)), operationId);
		if (result.status().equals("BUSY")) throw new RefreshInProgressException();
		if (!result.status().equals("RESERVED") || !session.sessionId().equals(tokenHasher.hash(result.familyId()))) {
			throw new UnauthorizedAccessException();
		}
		return new RefreshOperationResult(operationId, session.sessionId(), result.expiresAt());
	}

	public String cancel(String refreshToken, String operationId) {
		String result = refreshTokenStore.cancel(tokenHasher.hash(requireRefreshToken(refreshToken)), requireOperationId(operationId));
		if (result.equals("UNAUTHORIZED")) throw new UnauthorizedAccessException();
		return result;
	}

	private String requireOperationId(String operationId) {
		if (operationId == null || !operationId.matches("[A-Za-z0-9_-]{20,128}")) throw new UnauthorizedAccessException();
		return operationId;
	}

	public void logout(String refreshToken) {
		logout(refreshToken, null);
	}

	public void logout(String refreshToken, String expectedSessionId) {
		if (expectedSessionId != null) {
			String tokenHash = tokenHasher.hash(requireRefreshToken(refreshToken));
			String familyId = refreshTokenStore.findLogoutFamily(tokenHash);
			if (familyId == null || !tokenHasher.hash(familyId).equals(expectedSessionId)
				|| !refreshTokenStore.revokeIfFamily(tokenHash, familyId)) {
				throw new UnauthorizedAccessException();
			}
			return;
		}
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

	private boolean canRefresh(User user, Instant now) {
		return user.status() == UserStatus.ACTIVE
			|| (user.status() == UserStatus.WITHDRAWAL_PENDING
				&& user.withdrawalDueAt() != null
				&& now.isBefore(user.withdrawalDueAt()));
	}
}
