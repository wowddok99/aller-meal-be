package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.PasswordResetMailCommand;

public interface PasswordResetMailSender {

	void send(PasswordResetMailCommand command);
}
