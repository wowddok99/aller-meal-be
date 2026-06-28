package com.allermeal.domain.user;

import java.util.Base64;
import java.util.regex.Pattern;

public record EncryptedEmail(String value) {

	private static final Pattern KEY_VERSION = Pattern.compile("[A-Za-z0-9._-]+");

	public EncryptedEmail {
		value = UserValue.requireText(value, "암호화 이메일");
		String[] parts = value.split(":", -1);
		if (parts.length != 4 || !"v1".equals(parts[0]) || !KEY_VERSION.matcher(parts[1]).matches()) {
			throw new IllegalArgumentException("암호화 이메일은 versioned envelope 형식이어야 합니다.");
		}
		try {
			byte[] nonce = Base64.getDecoder().decode(parts[2]);
			byte[] ciphertext = Base64.getDecoder().decode(parts[3]);
			if (nonce.length != 12 || ciphertext.length < 16) {
				throw new IllegalArgumentException("암호화 이메일 envelope 값이 유효하지 않습니다.");
			}
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("암호화 이메일 envelope 값이 유효하지 않습니다.", exception);
		}
	}

	@Override
	public String toString() {
		return "EncryptedEmail[REDACTED]";
	}
}
