package com.allermeal.infra.auth;

import com.allermeal.application.port.out.PasswordResetMailSender;
import com.allermeal.application.port.out.command.PasswordResetMailCommand;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public final class SmtpPasswordResetMailSender implements PasswordResetMailSender {

	private final JavaMailSender mailSender;
	private final String from;
	private final String passwordResetBaseUrl;

	public SmtpPasswordResetMailSender(JavaMailSender mailSender, String from, String passwordResetBaseUrl) {
		this.mailSender = mailSender;
		this.from = from;
		this.passwordResetBaseUrl = passwordResetBaseUrl;
	}

	@Override
	public void send(PasswordResetMailCommand command) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(command.email());
		message.setSubject("Aller Meal 비밀번호 재설정");
		message.setText("""
			안녕하세요.

			아래 링크에서 비밀번호를 재설정해 주세요.
			%s%s

			재설정 토큰:
			%s
			""".formatted(passwordResetBaseUrl, command.token(), command.token()));
		mailSender.send(message);
	}
}
