package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.EmailVerificationMailCommand;

public interface EmailVerificationMailSender {

	void send(EmailVerificationMailCommand command);
}
