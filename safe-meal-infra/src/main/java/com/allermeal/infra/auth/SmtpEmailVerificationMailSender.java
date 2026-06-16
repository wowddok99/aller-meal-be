package com.allermeal.infra.auth;

import com.allermeal.application.port.out.EmailVerificationMailSender;
import com.allermeal.application.port.out.command.EmailVerificationMailCommand;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public final class SmtpEmailVerificationMailSender implements EmailVerificationMailSender {

	private final JavaMailSender mailSender;
	private final String from;
	private final String verificationBaseUrl;

	public SmtpEmailVerificationMailSender(JavaMailSender mailSender, String from, String verificationBaseUrl) {
		this.mailSender = mailSender;
		this.from = from;
		this.verificationBaseUrl = verificationBaseUrl;
	}

	@Override
	public void send(EmailVerificationMailCommand command) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(command.email());
		message.setSubject("Aller Meal 이메일 인증");
		message.setText("""
			안녕하세요.

			아래 링크로 이메일 인증을 완료해 주세요.
			%s%s

			인증 토큰:
			%s
			""".formatted(verificationBaseUrl, command.token(), command.token()));
		mailSender.send(message);
	}
}
