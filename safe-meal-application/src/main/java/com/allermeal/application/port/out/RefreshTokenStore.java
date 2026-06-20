package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.RefreshTokenCommand;
import com.allermeal.application.port.out.command.RotateRefreshTokenCommand;
import com.allermeal.application.port.out.result.RefreshTokenRotationResult;

public interface RefreshTokenStore {

	void store(RefreshTokenCommand command);

	RefreshTokenRotationResult rotate(RotateRefreshTokenCommand command);

	void revoke(String tokenHash);
}
