package com.allermeal.application.port.out.result;

import com.allermeal.domain.user.UserId;

public record RefreshTokenRotationResult(RefreshTokenRotationStatus status, UserId userId, Long sessionVersion) {

	public static RefreshTokenRotationResult rotated(UserId userId, long sessionVersion) {
		return new RefreshTokenRotationResult(RefreshTokenRotationStatus.ROTATED, userId, sessionVersion);
	}

	public static RefreshTokenRotationResult missing() {
		return new RefreshTokenRotationResult(RefreshTokenRotationStatus.MISSING, null, null);
	}

	public static RefreshTokenRotationResult reused() {
		return new RefreshTokenRotationResult(RefreshTokenRotationStatus.REUSED, null, null);
	}
}
