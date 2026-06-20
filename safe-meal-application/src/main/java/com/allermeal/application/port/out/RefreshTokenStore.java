package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.RefreshTokenCommand;
import com.allermeal.application.port.out.command.RotateRefreshTokenCommand;
import com.allermeal.application.port.out.result.RefreshTokenRotationResult;
import com.allermeal.domain.user.UserId;
import java.time.Duration;

public interface RefreshTokenStore {

	void store(RefreshTokenCommand command);

	RefreshTokenRotationResult rotate(RotateRefreshTokenCommand command);

	void revoke(String tokenHash);

	void revokeAll(UserId userId, Duration ttl);
}
