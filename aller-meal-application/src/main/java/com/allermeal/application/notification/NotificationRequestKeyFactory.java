package com.allermeal.application.notification;

import com.allermeal.application.port.out.result.PendingNotificationTargetResult;
import com.allermeal.domain.notification.NotificationChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class NotificationRequestKeyFactory {

	private NotificationRequestKeyFactory() {
	}

	public static String correctionKey(PendingNotificationTargetResult target, NotificationChannel channel) {
		return sha256Hex("channel=%s\nchild=%s\ndate=%s".formatted(
			channel, target.childProfileId().value(), target.notificationDate()));
	}

	public static String contentVersion(PendingNotificationTargetResult target) {
		return sha256Hex("reason=%s\nriskLevel=%s\nriskVersion=%s\nmealCount=%d".formatted(
			target.reason(), target.riskLevel(), target.riskVersion(), target.mealCount()));
	}

	public static String dedupKey(PendingNotificationTargetResult target, NotificationChannel channel) {
		return sha256Hex("correctionKey=%s\ncontentVersion=%s".formatted(
			correctionKey(target, channel), contentVersion(target)));
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
		}
	}
}
