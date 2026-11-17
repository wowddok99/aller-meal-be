package com.allermeal.infra.notification;

import static org.junit.jupiter.api.Assertions.*;

import com.allermeal.application.port.out.command.*;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.notification.*;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

final class SmtpNotificationMailSenderTest {
	private static final ChildProfileId CHILD = new ChildProfileId(UUID.randomUUID());
	private static final NotificationId NOTIFICATION = new NotificationId(UUID.randomUUID());
	private static final String LINK = "https://parents.example.test/children/" + CHILD.value() + "/meals?date=2026-11-13&mode=daily";
	private final CapturingMailSender mail = new CapturingMailSender();

	@Test
	void mimeContainsPlainHtmlInlineLogoEscapedRealDetailsAndTrustedDailyLink() throws Exception {
		new SmtpNotificationMailSender(mail, "sender@example.test", "https://parents.example.test/")
			.send(command(NotificationReason.RISK_DETECTED, List.of(new NotificationMealItemCommand(
				MealType.LUNCH, "메뉴 <우유> & \"이름\"", List.of("성분 <우유> & '이름'")))));
		assertNotNull(mail.sent);
		mail.sent.saveChanges();
		assertEquals("owner@example.test", mail.sent.getAllRecipients()[0].toString());
		assertTrue(mail.sent.getSubject().contains("알레르기"));
		Map<String, String> bodies = bodies(mail.sent);
		String plain = bodies.get("text/plain"), html = bodies.get("text/html");
		assertNotNull(plain); assertNotNull(html);
		assertTrue(plain.contains("자녀 <이름> & \"'"));
		assertTrue(plain.contains("학교 <이름> & \"'"));
		assertTrue(plain.contains("2026-11-13"));
		assertTrue(plain.contains(LINK));
		assertTrue(html.contains("href=\"" + LINK.replace("&", "&amp;") + "\""));
		assertTrue(html.contains("자녀 &lt;이름&gt; &amp; &quot;&#39;"));
		assertTrue(html.contains("학교 &lt;이름&gt; &amp; &quot;&#39;"));
		assertTrue(html.contains("메뉴 &lt;우유&gt; &amp; &quot;이름&quot;"));
		assertTrue(html.contains("성분 &lt;우유&gt; &amp; &#39;이름&#39;"));
		assertFalse(html.contains("<우유>"));
		assertTrue(html.contains("cid:allermeal-logo"));
		assertTrue(html.contains("AllerMeal"));
		assertTrue(html.contains("background:#00b86b"));
		assertTrue(html.contains("※ 알림 설정은 자녀의 알림 설정에서 변경하실 수 있습니다."));
		assertEquals("image/png", bodies.get("inline-logo"));
		assertFalse(plain.contains(NOTIFICATION.value().toString()));
		for (NotificationReason reason : NotificationReason.values()) {
			assertFalse(plain.contains(reason.name())); assertFalse(html.contains(reason.name()));
		}
	}

	@Test
	void allSixReasonsRemainDistinctWithDetailFreeFallbackAndCarefulUncertaintyWording() throws Exception {
		Set<String> subjects = new HashSet<>();
		for (NotificationReason reason : NotificationReason.values()) {
			new SmtpNotificationMailSender(mail, "sender@example.test", "https://parents.example.test")
				.send(command(reason, List.of()));
			mail.sent.saveChanges();
			Map<String, String> parts = bodies(mail.sent);
			String plain = parts.get("text/plain"), html = parts.get("text/html");
			assertNotNull(plain); assertNotNull(html);
			subjects.add(mail.sent.getSubject());
			assertTrue(plain.contains("2026-11-13"));
			assertTrue(plain.contains("학교 <이름>"));
			assertTrue(plain.contains(LINK));
			assertFalse(html.contains("<th"));
			assertFalse(plain.contains(reason.name()));
			assertFalse(html.contains(NOTIFICATION.value().toString()));
			if (reason == NotificationReason.NO_RISK) assertTrue(plain.contains("안전을 보장"));
			if (reason == NotificationReason.NO_MEAL) assertTrue(plain.contains("급식 미제공을 뜻하지는 않습니다"));
		}
		assertEquals(6, subjects.size());
	}

	@Test
	void originRejectsUntrustedPathsCredentialsSchemesQueriesAndFragments() {
		for (String invalid : List.of("javascript:alert(1)", "//evil.test", "https://user:password@evil.test", "https://good.test/redirect",
			"https://good.test?next=evil", "https://good.test#evil", "https://good.test/%2f%2fevil", "https://good.test\\@evil.test", "https://good.test:99999")) {
			assertThrows(IllegalArgumentException.class, () -> new SmtpNotificationMailSender(mail, "sender@example.test", invalid), invalid);
		}
		assertDoesNotThrow(() -> new SmtpNotificationMailSender(mail, "sender@example.test", "http://localhost:3000"));
	}

	private NotificationMailCommand command(NotificationReason reason, List<NotificationMealItemCommand> items) {
		return new NotificationMailCommand(NOTIFICATION, "owner@example.test", CHILD, "자녀 <이름> & \"'", "학교 <이름> & \"'",
			LocalDate.of(2026, 11, 13), reason, items);
	}

	private Map<String, String> bodies(Part part) throws Exception {
		Map<String, String> parts = new HashMap<>();
		if (part.isMimeType("text/plain")) parts.put("text/plain", (String) part.getContent());
		else if (part.isMimeType("text/html")) parts.put("text/html", (String) part.getContent());
		else if (part.isMimeType("multipart/*")) {
			Multipart multipart = (Multipart) part.getContent();
			for (int i = 0; i < multipart.getCount(); i++) parts.putAll(bodies(multipart.getBodyPart(i)));
		} else if (Arrays.asList(Optional.ofNullable(part.getHeader("Content-ID")).orElse(new String[0])).contains("<allermeal-logo>")) {
			assertTrue(part.isMimeType("image/png"));
			assertTrue(part.getInputStream().readAllBytes().length > 0);
			parts.put("inline-logo", "image/png");
		}
		return parts;
	}

	private static final class CapturingMailSender extends JavaMailSenderImpl {
		private MimeMessage sent;
		@Override public void send(MimeMessage message) { sent = message; }
		@Override public void send(SimpleMailMessage message) { fail("HTML/plain MIME 메일이어야 합니다."); }
	}
}
