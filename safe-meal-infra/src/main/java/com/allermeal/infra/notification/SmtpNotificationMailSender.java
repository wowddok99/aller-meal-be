package com.allermeal.infra.notification;

import com.allermeal.application.port.out.NotificationMailSender;
import com.allermeal.application.port.out.command.NotificationMailCommand;
import java.util.Objects;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public final class SmtpNotificationMailSender implements NotificationMailSender {

	private final JavaMailSender mailSender;
	private final String from;

	public SmtpNotificationMailSender(JavaMailSender mailSender, String from) {
		this.mailSender = Objects.requireNonNull(mailSender, "JavaMailSender는 null일 수 없습니다.");
		this.from = requireText(from);
	}

	@Override
	public void send(NotificationMailCommand command) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(command.recipientEmail());
		message.setSubject(command.subject());
		message.setText(command.body());
		mailSender.send(message);
	}

	private static String requireText(String value) {
		Objects.requireNonNull(value, "알림 메일 발신자는 null일 수 없습니다.");
		String normalized = value.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("알림 메일 발신자는 비어 있을 수 없습니다.");
		}
		return normalized;
	}
}
