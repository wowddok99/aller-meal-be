package com.allermeal.application.port.out.result;

import com.allermeal.domain.user.UserId;

public record RefreshTokenRotationResult(RefreshTokenRotationStatus status, UserId userId) {

	public static RefreshTokenRotationResult rotated(UserId userId) {
		return new RefreshTokenRotationResult(RefreshTokenRotationStatus.ROTATED, userId);
	}

	public static RefreshTokenRotationResult missing() {
		return new RefreshTokenRotationResult(RefreshTokenRotationStatus.MISSING, null);
	}

	public static RefreshTokenRotationResult reused() {
		return new RefreshTokenRotationResult(RefreshTokenRotationStatus.REUSED, null);
	}
}
