package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.PasswordResetTokenCommand;
import com.allermeal.domain.user.UserId;
import java.util.Optional;

public interface PasswordResetTokenStore {

	void store(PasswordResetTokenCommand command);

	Optional<UserId> consume(String tokenHash);
}
