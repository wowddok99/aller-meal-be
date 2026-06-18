package com.allermeal.infra.auth;

import com.allermeal.application.port.out.PasswordHasher;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Pbkdf2PasswordHasher implements PasswordHasher {

	private static final int SALT_BYTES = 16;
	private static final int KEY_BITS = 256;

	private final SecureRandom secureRandom;
	private final int iterations;

	public Pbkdf2PasswordHasher(SecureRandom secureRandom, int iterations) {
		if (iterations < 100_000) {
			throw new IllegalArgumentException("PBKDF2 반복 횟수는 100000 이상이어야 합니다.");
		}
		this.secureRandom = secureRandom;
		this.iterations = iterations;
	}

	@Override
	public String hash(String password) {
		try {
			byte[] salt = new byte[SALT_BYTES];
			secureRandom.nextBytes(salt);
			PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
			byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
			return "pbkdf2-sha256:v1:" + iterations + ":" + Base64.getEncoder().encodeToString(salt)
				+ ":" + Base64.getEncoder().encodeToString(hash);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("비밀번호 해시에 실패했습니다.", exception);
		}
	}
}
