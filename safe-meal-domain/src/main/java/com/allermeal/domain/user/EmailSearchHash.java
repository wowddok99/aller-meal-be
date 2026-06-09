package com.allermeal.domain.user;

import java.util.regex.Pattern;

public record EmailSearchHash(String value) {

	private static final Pattern LOWERCASE_HEX_SHA_256 = Pattern.compile("[0-9a-f]{64}");

	public EmailSearchHash {
		value = UserValue.requireText(value, "이메일 검색 해시");
		if (!LOWERCASE_HEX_SHA_256.matcher(value).matches()) {
			throw new IllegalArgumentException("이메일 검색 해시는 64자리 lowercase hex여야 합니다.");
		}
	}

	@Override
	public String toString() {
		return "EmailSearchHash[REDACTED]";
	}
}
