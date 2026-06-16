package com.allermeal.infra.auth;

import com.allermeal.application.port.out.EmailVerificationTokenHasher;

public final class Sha256EmailVerificationTokenHasher implements EmailVerificationTokenHasher {

	@Override
	public String hash(String token) {
		return Sha256Hex.hash(token);
	}
}
