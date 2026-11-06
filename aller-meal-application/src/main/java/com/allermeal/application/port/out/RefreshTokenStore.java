package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.RefreshTokenCommand;
import com.allermeal.application.port.out.command.RotateRefreshTokenCommand;
import com.allermeal.application.port.out.result.RefreshTokenRotationResult;
import com.allermeal.application.port.out.result.RefreshSessionLookupResult;
import com.allermeal.application.port.out.result.RefreshOperationReservationResult;
import com.allermeal.application.port.out.result.RefreshTokenLoginSnapshotResult;
import com.allermeal.domain.user.UserId;
import java.time.Duration;

public interface RefreshTokenStore {

	default RefreshTokenLoginSnapshotResult loginSnapshot(UserId userId) {
		throw new UnsupportedOperationException("로그인 인증 시점 조회를 지원하지 않습니다.");
	}

	void store(RefreshTokenCommand command);

	RefreshTokenRotationResult rotate(RotateRefreshTokenCommand command);

	default RefreshSessionLookupResult lookup(String tokenHash) {
		throw new UnsupportedOperationException("세션 조회를 지원하지 않습니다.");
	}

	default RefreshOperationReservationResult reserve(String tokenHash, String operationId) {
		throw new UnsupportedOperationException("갱신 예약을 지원하지 않습니다.");
	}

	default String cancel(String tokenHash, String operationId) {
		throw new UnsupportedOperationException("갱신 예약 취소를 지원하지 않습니다.");
	}

	void revoke(String tokenHash);

	default String findLogoutFamily(String tokenHash) {
		throw new UnsupportedOperationException("로그아웃 세션 조회를 지원하지 않습니다.");
	}

	default boolean revokeIfFamily(String tokenHash, String familyId) {
		throw new UnsupportedOperationException("조건부 로그아웃을 지원하지 않습니다.");
	}

	void revokeAll(UserId userId, Duration ttl);
}
