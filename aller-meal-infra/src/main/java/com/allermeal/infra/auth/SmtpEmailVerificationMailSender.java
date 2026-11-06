package com.allermeal.infra.auth;

import com.allermeal.application.port.out.EmailVerificationMailSender;
import com.allermeal.application.port.out.command.EmailVerificationMailCommand;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

public final class SmtpEmailVerificationMailSender implements EmailVerificationMailSender {

	private final JavaMailSender mailSender;
	private final String from;
	private final String verificationBaseUrl;

	public SmtpEmailVerificationMailSender(JavaMailSender mailSender, String from, String verificationBaseUrl) {
		this.mailSender = mailSender;
		this.from = from;
		this.verificationBaseUrl = verificationBaseUrl;
	}

	private static String escapeHtml(String value) {
		return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
	}

	@Override
	public void send(EmailVerificationMailCommand command) {
		String link = verificationBaseUrl + command.token();
		MimeMessage message = mailSender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(from);
			helper.setTo(command.email());
			helper.setSubject("AllerMeal 이메일 인증");
			String plain = """
				안녕하세요. AllerMeal입니다.

				회원가입을 완료하려면 아래 링크를 열어 이메일을 인증해 주세요.
				%s

				※ 직접 요청하지 않았다면 이 메일을 무시해 주세요.
				""".formatted(link);
			String html = """
				<!doctype html><html lang="ko"><body style="margin:0;padding:40px 24px;background:#fff;font-family:Arial,sans-serif;color:#27272a;">
				<table role="presentation" style="max-width:560px;width:100%%;margin:0 auto;border-collapse:collapse;"><tr><td>
				<table role="presentation" style="border-collapse:collapse;"><tr><td style="vertical-align:middle;padding-right:8px;"><img src="cid:allermeal-logo" alt="" width="32" height="32" style="display:block;border:0;"></td><td style="vertical-align:middle;font-size:23px;font-weight:bold;letter-spacing:-1px;color:#09090b;">AllerMeal</td></tr></table>
				<h1 style="margin:30px 0 20px;font-size:24px;line-height:1.4;">이메일 인증 안내</h1>
				<p style="margin:0;font-size:14px;line-height:1.7;color:#52525b;">AllerMeal 회원가입을 완료하려면 아래 버튼을 눌러<br>이메일 인증을 완료해 주세요.</p>
				<p style="margin:20px 0 32px;"><a href="%s" style="display:inline-block;padding:12px 18px;border-radius:6px;background:#00b86b;color:#fff;text-decoration:none;font-size:14px;font-weight:bold;">이메일 인증하기</a></p>
				<div style="border-top:1px solid #e4e4e7;padding-top:20px;font-size:12px;line-height:1.6;color:#71717a;">※ 직접 요청하지 않았다면 이 메일을 무시해 주세요.</div>
				</td></tr></table></body></html>
				""".formatted(escapeHtml(link));
			helper.setText(plain, html);
			helper.addInline("allermeal-logo", new ClassPathResource("mail/allermeal-logo.png"), "image/png");
		} catch (MessagingException exception) {
			throw new MailPreparationException("이메일 인증 메일을 준비하지 못했습니다.", exception);
		}
		mailSender.send(message);
	}
}
