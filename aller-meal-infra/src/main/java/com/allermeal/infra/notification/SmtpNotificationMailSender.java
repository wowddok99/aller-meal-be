package com.allermeal.infra.notification;

import com.allermeal.application.port.out.NotificationMailSender;
import com.allermeal.application.port.out.command.NotificationMailCommand;
import com.allermeal.application.port.out.command.NotificationMealItemCommand;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.notification.NotificationReason;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

public final class SmtpNotificationMailSender implements NotificationMailSender {

	private static final String FOOTER = "※ 알림 설정은 자녀의 알림 설정에서 변경하실 수 있습니다.";
	private final JavaMailSender mailSender;
	private final String from;
	private final String frontendOrigin;

	public SmtpNotificationMailSender(JavaMailSender mailSender, String from, String frontendOrigin) {
		this.mailSender = Objects.requireNonNull(mailSender, "JavaMailSender는 null일 수 없습니다.");
		this.from = requireText(from);
		this.frontendOrigin = validatedOrigin(frontendOrigin);
	}

	@Override
	public void send(NotificationMailCommand command) {
		String link = frontendOrigin + "/children/" + command.childProfileId().value()
			+ "/meals?date=" + command.notificationDate() + "&mode=daily";
		ReasonText reason = reasonText(command.reason());
		MimeMessage message = mailSender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(from);
			helper.setTo(command.recipientEmail());
			helper.setSubject("AllerMeal " + reason.title());
			helper.setText(plain(command, reason, link), html(command, reason, link));
			// Spring MimeMessageHelper 계약: 본문 설정 뒤 inline resource를 추가한다.
			helper.addInline("allermeal-logo", new ClassPathResource("mail/allermeal-logo.png"), "image/png");
		} catch (MessagingException exception) {
			throw new MailPreparationException("급식 알림 메일을 준비하지 못했습니다.", exception);
		}
		mailSender.send(message);
	}

	private String plain(NotificationMailCommand command, ReasonText reason, String link) {
		String details = command.items().isEmpty() ? "" : "\n확인된 메뉴 요약\n" + command.items().stream()
			.map(item -> mealName(item.mealType()) + " · " + item.menuName() + ": " + matchedText(item))
			.collect(Collectors.joining("\n")) + "\n";
		return """
			안녕하세요. AllerMeal입니다.

			%s
			자녀: %s
			학교: %s
			알림 날짜: %s

			%s
			%s
			알림 생성 당시 확인 결과입니다. 최신 급식 정보와 자세한 내용은 아래 링크에서 확인해 주세요.
			%s

			%s
			""".formatted(reason.title(), command.childName(), command.schoolName(), command.notificationDate(),
			reason.description(), details, link, FOOTER);
	}

	private String html(NotificationMailCommand command, ReasonText reason, String link) {
		return """
			<!doctype html><html lang="ko"><body style="margin:0;padding:40px 24px;background:#fff;font-family:Arial,sans-serif;color:#27272a;">
			<table role="presentation" style="max-width:560px;width:100%%;margin:0 auto;border-collapse:collapse;"><tr><td>
			<table role="presentation" style="border-collapse:collapse;"><tr><td style="vertical-align:middle;padding-right:8px;"><img src="cid:allermeal-logo" alt="" width="32" height="32" style="display:block;border:0;"></td><td style="vertical-align:middle;font-size:23px;font-weight:bold;letter-spacing:-1px;color:#09090b;">AllerMeal</td></tr></table>
			<h1 style="margin:30px 0 20px;font-size:24px;line-height:1.4;">%s</h1>
			<p style="font-size:14px;line-height:1.7;">자녀: %s<br>학교: %s<br>알림 날짜: %s</p>
			<p style="font-size:14px;line-height:1.7;color:#52525b;">%s</p>
			%s
			<p style="font-size:13px;line-height:1.7;color:#52525b;">알림 생성 당시 확인 결과입니다. 최신 급식 정보와 자세한 내용은 아래에서 확인해 주세요.</p>
			<p style="margin:20px 0 32px;"><a href="%s" style="display:inline-block;padding:12px 18px;border-radius:6px;background:#00b86b;color:#fff;text-decoration:none;font-size:14px;font-weight:bold;">급식 상세 확인하기</a></p>
			<div style="border-top:1px solid #e4e4e7;padding-top:20px;font-size:12px;line-height:1.6;color:#71717a;">%s</div>
			</td></tr></table></body></html>
			""".formatted(escapeHtml(reason.title()), escapeHtml(command.childName()), escapeHtml(command.schoolName()),
			escapeHtml(command.notificationDate().toString()), escapeHtml(reason.description()), detailsHtml(command.items()),
			escapeHtml(link), FOOTER);
	}

	private String detailsHtml(List<NotificationMealItemCommand> items) {
		if (items.isEmpty()) return "";
		String rows = items.stream().map(item -> "<tr><td style=\"padding:8px;border-bottom:1px solid #e4e4e7;\">"
			+ mealName(item.mealType()) + "</td><td style=\"padding:8px;border-bottom:1px solid #e4e4e7;\">"
			+ escapeHtml(item.menuName()) + "</td><td style=\"padding:8px;border-bottom:1px solid #e4e4e7;\">"
			+ escapeHtml(matchedText(item)) + "</td></tr>").collect(Collectors.joining());
		return "<h2 style=\"font-size:16px;\">확인된 메뉴 요약</h2>"
			+ "<table style=\"width:100%;border-collapse:collapse;font-size:13px;text-align:left;\">"
			+ "<thead><tr><th scope=\"col\">식사</th><th scope=\"col\">메뉴</th><th scope=\"col\">등록 알레르기 매칭 성분</th></tr></thead>"
			+ "<tbody>" + rows + "</tbody></table>";
	}

	private String matchedText(NotificationMealItemCommand item) {
		return item.matchedAllergens().isEmpty() ? "확인된 일치 성분 없음" : String.join(", ", item.matchedAllergens());
	}

	private String mealName(MealType type) {
		return switch (type) { case BREAKFAST -> "아침"; case LUNCH -> "점심"; case DINNER -> "저녁"; };
	}

	private ReasonText reasonText(NotificationReason reason) {
		return switch (reason) {
			case RISK_DETECTED -> new ReasonText("급식 알레르기 관련 성분이 확인되었습니다",
				"등록된 알레르기와 관련된 성분이 급식 정보에서 확인되었습니다. 메뉴와 성분을 확인하시고 학교의 급식 안내도 함께 살펴봐 주세요.");
			case NO_RISK -> new ReasonText("급식에서 확인된 일치 성분이 없습니다",
				"등록된 알레르기와 확인된 급식 정보를 비교한 결과, 일치하는 성분을 찾지 못했습니다. 모든 음식의 안전을 보장하는 것은 아니므로 학교의 급식 안내도 함께 확인해 주세요.");
			case RISK_UNKNOWN -> new ReasonText("급식 성분 정보가 부족합니다",
				"급식 성분 정보가 부족하여 등록된 알레르기와의 관련 여부를 확인하기 어렵습니다. 안전하다는 의미는 아니므로 학교에 성분을 확인해 주세요.");
			case RISK_LABELING_FAILED -> new ReasonText("급식 성분 확인을 완료하지 못했습니다",
				"급식 성분 확인 처리에 실패하여 알레르기 관련 여부를 안내해 드리기 어렵습니다. 안전 여부가 확인된 것은 아니므로 학교의 급식 안내를 확인해 주세요.");
			case RISK_PENDING -> new ReasonText("급식 성분을 확인하고 있습니다",
				"급식 정보 수집 또는 성분 확인이 아직 진행 중입니다. 알레르기 관련 여부가 확정되지 않았으므로 최신 급식 정보를 다시 확인해 주세요.");
			case NO_MEAL -> new ReasonText("해당 날짜의 급식 조회 결과가 없습니다",
				"해당 날짜에 조회된 급식 정보가 없습니다. 실제 급식 미제공을 뜻하지는 않습니다. 학교의 급식 일정과 최신 정보를 확인해 주세요.");
		};
	}

	private static String escapeHtml(String value) {
		return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
	}

	private static String validatedOrigin(String value) {
		try {
			URI uri = new URI(Objects.requireNonNull(value, "알림 FE origin은 null일 수 없습니다.").trim());
			if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
				|| uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
				|| !(uri.getRawPath().isEmpty() || uri.getRawPath().equals("/")) || uri.getPort() < -1 || uri.getPort() > 65535) {
				throw new IllegalArgumentException("알림 FE origin은 경로·사용자 정보·query·fragment 없는 HTTP(S) origin이어야 합니다.");
			}
			return new URI(uri.getScheme().toLowerCase(java.util.Locale.ROOT), null, uri.getHost(), uri.getPort(), null, null, null).toASCIIString();
		} catch (URISyntaxException exception) {
			throw new IllegalArgumentException("알림 FE origin 형식이 올바르지 않습니다.", exception);
		}
	}

	private static String requireText(String value) {
		Objects.requireNonNull(value, "알림 메일 발신자는 null일 수 없습니다.");
		String normalized = value.trim();
		if (normalized.isEmpty()) throw new IllegalArgumentException("알림 메일 발신자는 비어 있을 수 없습니다.");
		return normalized;
	}

	private record ReasonText(String title, String description) { }
}
