package com.allermeal.infra.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.allermeal.application.port.out.command.EmailVerificationMailCommand;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpEmailVerificationMailSenderTest {
	@Test
	void sendsVerificationLinkInPlainAndHtmlWithoutSeparateToken() throws Exception {
		var mail = new JavaMailSenderImpl() {
			MimeMessage sent;
			@Override public void send(MimeMessage message) { sent = message; }
		};
		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));

		new SmtpEmailVerificationMailSender(mail, "hello@example.test", "http://localhost:3000/auth/email-verification/confirm?token=")
			.send(new EmailVerificationMailCommand("user@example.test", "test-token"));
		message = mail.sent;
		assertNotNull(message);
		assertEquals("AllerMeal 이메일 인증", message.getSubject());
		var root = (jakarta.mail.Multipart) message.getContent();
		var related = (jakarta.mail.Multipart) root.getBodyPart(0).getContent();
		var parts = (jakarta.mail.Multipart) related.getBodyPart(0).getContent();
		String plain = (String) parts.getBodyPart(0).getContent();
		String html = (String) parts.getBodyPart(1).getContent();
		assertTrue(plain.contains("confirm?token=test-token"));
		assertFalse(plain.contains("인증 토큰:"));
		assertTrue(html.contains("이메일 인증하기"));
		assertTrue(html.contains("cid:allermeal-logo"));
		assertEquals("<allermeal-logo>", related.getBodyPart(1).getHeader("Content-ID")[0]);
		assertTrue(html.contains("confirm?token=test-token"));
	}
}
