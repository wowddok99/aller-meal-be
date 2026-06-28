package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.NotificationMailCommand;

public interface NotificationMailSender {

	void send(NotificationMailCommand command);
}
