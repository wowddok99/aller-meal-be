package com.allermeal.infra.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.allermeal.application.port.out.command.PasswordResetMailCommand;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

final class SmtpPasswordResetMailSenderTest {

	private static final String BASE_URL = "http://localhost:3000/auth/password-reset/confirm?token=";

	@Test
	void sendsBrandedMultipartMailWithInlineLogoAndOneLinkPerAlternative() throws Exception {
		var mail = new CapturingMailSender();
		new SmtpPasswordResetMailSender(mail, "hello@example.test", BASE_URL)
			.send(new PasswordResetMailCommand("user@example.test", "test-reset-token"));

		MimeMessage message = mail.serializedMessage();
		assertEquals("hello@example.test", message.getFrom()[0].toString());
		assertEquals("user@example.test", message.getAllRecipients()[0].toString());
		assertEquals("비밀번호 재설정 안내", message.getSubject());
		var root = (Multipart) message.getContent();
		assertTrue(root.getContentType().startsWith("multipart/mixed"));
		var related = (Multipart) root.getBodyPart(0).getContent();
		assertTrue(related.getContentType().startsWith("multipart/related"));
		var alternatives = (Multipart) related.getBodyPart(0).getContent();
		assertTrue(alternatives.getContentType().startsWith("multipart/alternative"));
		assertEquals(2, alternatives.getCount());
		assertTrue(alternatives.getBodyPart(0).isMimeType("text/plain"));
		assertTrue(alternatives.getBodyPart(1).isMimeType("text/html"));
		String plain = (String) alternatives.getBodyPart(0).getContent();
		String html = (String) alternatives.getBodyPart(1).getContent();
		assertTrue(plain.contains(BASE_URL + "test-reset-token"));
		assertTrue(html.contains("href=\"" + BASE_URL + "test-reset-token\""));
		assertEquals(1, occurrences(plain, "test-reset-token"));
		assertEquals(1, occurrences(html, "test-reset-token"));
		assertFalse(plain.contains("재설정 토큰:"));
		assertFalse(html.contains("재설정 토큰:"));
		assertTrue(html.contains("AllerMeal"));
		assertTrue(html.contains("비밀번호 재설정 안내"));
		assertTrue(html.contains("비밀번호 재설정하기"));
		assertTrue(html.contains("background:#00b86b"));
		assertTrue(html.contains("cid:allermeal-logo"));
		assertTrue(html.contains("※ 직접 요청하지 않았다면 이 메일을 무시해 주세요."));
		assertTrue(plain.contains("※ 직접 요청하지 않았다면 이 메일을 무시해 주세요."));
		var logo = related.getBodyPart(1);
		assertEquals("<allermeal-logo>", logo.getHeader("Content-ID")[0]);
		assertEquals("inline", logo.getDisposition());
		assertTrue(logo.isMimeType("image/png"));
		try (var expected = new ClassPathResource("mail/allermeal-logo.png").getInputStream();
			var actual = logo.getInputStream()) {
			assertArrayEquals(expected.readAllBytes(), actual.readAllBytes());
		}
	}

	@Test
	void escapesHtmlLinkWithoutChangingPlainAlternative() throws Exception {
		var mail = new CapturingMailSender();
		String base = "http://localhost:3000/auth/password-reset/confirm?source=mail&token=";
		String token = "test<>&\"'token";
		new SmtpPasswordResetMailSender(mail, "hello@example.test", base)
			.send(new PasswordResetMailCommand("user@example.test", token));

		var root = (Multipart) mail.serializedMessage().getContent();
		var related = (Multipart) root.getBodyPart(0).getContent();
		var alternatives = (Multipart) related.getBodyPart(0).getContent();
		String plain = (String) alternatives.getBodyPart(0).getContent();
		String html = (String) alternatives.getBodyPart(1).getContent();
		assertTrue(plain.contains(base + token));
		assertTrue(html.contains("href=\"http://localhost:3000/auth/password-reset/confirm?source=mail&amp;token=test&lt;&gt;&amp;&quot;&#39;token\""));
		assertFalse(html.contains(token));
	}

	private int occurrences(String text, String value) {
		return (text.length() - text.replace(value, "").length()) / value.length();
	}

	private static final class CapturingMailSender extends JavaMailSenderImpl {
		private MimeMessage sent;

		@Override
		public void send(MimeMessage message) { sent = message; }

		@Override
		public void send(SimpleMailMessage message) {
			fail("HTML과 plain 대체본문이 포함된 MIME 메일을 보내야 합니다.");
		}

		private MimeMessage serializedMessage() throws Exception {
			assertNotNull(sent, "HTML과 plain 대체본문이 포함된 MIME 메일을 보내야 합니다.");
			var bytes = new ByteArrayOutputStream();
			sent.writeTo(bytes);
			return new MimeMessage(getSession(), new ByteArrayInputStream(bytes.toByteArray()));
		}
	}
}
