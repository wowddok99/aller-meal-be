package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.EmailVerificationTokenCommand;
import com.allermeal.domain.user.UserId;
import java.util.Optional;

public interface EmailVerificationTokenStore {

	void store(EmailVerificationTokenCommand command);

	Optional<UserId> consume(String tokenHash);
}
