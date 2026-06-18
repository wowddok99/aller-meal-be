package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.RefreshTokenCommand;

public interface RefreshTokenStore {

	void store(RefreshTokenCommand command);
}
