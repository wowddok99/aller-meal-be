package com.allermeal.application.port.out.result;

import com.allermeal.domain.user.UserId;
import java.time.Instant;

public record RefreshTokenRotationResult(
	RefreshTokenRotationStatus status, UserId userId, Long sessionVersion,
	Boolean rememberMe, Instant absoluteExpiresAt, Instant refreshExpiresAt, String familyId
) {
	public static RefreshTokenRotationResult rotated(UserId userId, long sessionVersion) {
		return rotated(userId, sessionVersion, null, null, null, null);
	}

	public static RefreshTokenRotationResult rotated(UserId userId, long sessionVersion, Boolean rememberMe,
		Instant absoluteExpiresAt, Instant refreshExpiresAt, String familyId) {
		return new RefreshTokenRotationResult(RefreshTokenRotationStatus.ROTATED, userId, sessionVersion,
			rememberMe, absoluteExpiresAt, refreshExpiresAt, familyId);
	}
	public static RefreshTokenRotationResult operationExpired() {
		return new RefreshTokenRotationResult(RefreshTokenRotationStatus.OPERATION_EXPIRED, null, null, null, null, null, null);
	}
	public static RefreshTokenRotationResult missing() {
		return new RefreshTokenRotationResult(RefreshTokenRotationStatus.MISSING, null, null, null, null, null, null);
	}
	public static RefreshTokenRotationResult reused() {
		return new RefreshTokenRotationResult(RefreshTokenRotationStatus.REUSED, null, null, null, null, null, null);
	}
}
