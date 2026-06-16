package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.EmailVerificationTokenCommand;

public interface EmailVerificationTokenStore {

	void store(EmailVerificationTokenCommand command);
}
