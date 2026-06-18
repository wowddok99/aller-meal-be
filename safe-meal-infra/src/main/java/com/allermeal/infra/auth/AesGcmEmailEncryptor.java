package com.allermeal.infra.auth;

import com.allermeal.application.port.out.EmailEncryptor;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmEmailEncryptor implements EmailEncryptor {

	private static final int NONCE_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKeySpec key;
	private final String keyVersion;
	private final SecureRandom secureRandom;

	public AesGcmEmailEncryptor(byte[] keyBytes, String keyVersion, SecureRandom secureRandom) {
		if (keyBytes.length != 32) {
			throw new IllegalArgumentException("이메일 암호화 key는 32 bytes여야 합니다.");
		}
		this.key = new SecretKeySpec(keyBytes, "AES");
		this.keyVersion = keyVersion;
		this.secureRandom = secureRandom;
	}

	@Override
	public String encrypt(String normalizedEmail) {
		try {
			byte[] nonce = new byte[NONCE_BYTES];
			secureRandom.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
			byte[] ciphertext = cipher.doFinal(normalizedEmail.getBytes(StandardCharsets.UTF_8));
			return "v1:" + keyVersion + ":" + Base64.getEncoder().encodeToString(nonce)
				+ ":" + Base64.getEncoder().encodeToString(ciphertext);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("이메일 암호화에 실패했습니다.", exception);
		}
	}
}
