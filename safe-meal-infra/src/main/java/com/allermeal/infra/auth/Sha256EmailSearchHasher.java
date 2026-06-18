package com.allermeal.infra.auth;

import com.allermeal.application.port.out.EmailSearchHasher;
import com.allermeal.domain.user.EmailSearchHash;

public final class Sha256EmailSearchHasher implements EmailSearchHasher {

	@Override
	public EmailSearchHash hash(String normalizedEmail) {
		return new EmailSearchHash(Sha256Hex.hash(normalizedEmail));
	}
}
