package com.allermeal.infra.auth;

import com.allermeal.application.auth.AccessTokenClaims;
import com.allermeal.application.auth.UnauthorizedAccessException;
import com.allermeal.application.port.out.AccessTokenIssuer;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class HmacSha256AccessTokenIssuer implements AccessTokenIssuer {

	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

	private final byte[] signingKey;
	private final Clock clock;
	private final ObjectMapper objectMapper;

	public HmacSha256AccessTokenIssuer(byte[] signingKey, Clock clock, ObjectMapper objectMapper) {
		if (signingKey.length < 32) {
			throw new IllegalArgumentException("Access Token 서명 key는 32 bytes 이상이어야 합니다.");
		}
		this.signingKey = signingKey.clone();
		this.clock = clock;
		this.objectMapper = objectMapper;
	}

	@Override
	public String issue(User user, Duration ttl) {
		Instant expiresAt = clock.instant().plus(ttl);
		String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sub", user.id().value().toString());
		payload.put("role", user.role().name());
		payload.put("status", user.status().name());
		payload.put("emailVerificationStatus", user.emailVerificationStatus().name());
		payload.put("exp", expiresAt.getEpochSecond());
		String body = encodeJson(payload);
		String signatureInput = header + "." + body;
		return signatureInput + "." + sign(signatureInput);
	}

	@Override
	public AccessTokenClaims verify(String token) {
		try {
			String[] parts = token.split("\\.", -1);
			if (parts.length != 3) {
				throw new UnauthorizedAccessException();
			}
			String signatureInput = parts[0] + "." + parts[1];
			if (!signatureMatches(signatureInput, parts[2])) {
				throw new UnauthorizedAccessException();
			}
			JsonNode payload = objectMapper.readTree(new String(BASE64_URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8));
			Instant expiresAt = Instant.ofEpochSecond(payload.get("exp").asLong());
			if (!expiresAt.isAfter(clock.instant())) {
				throw new UnauthorizedAccessException();
			}
			return new AccessTokenClaims(
				new UserId(UUID.fromString(payload.get("sub").asText())),
				UserRole.valueOf(payload.get("role").asText()),
				UserStatus.valueOf(payload.get("status").asText()),
				EmailVerificationStatus.valueOf(payload.get("emailVerificationStatus").asText()),
				expiresAt);
		} catch (UnauthorizedAccessException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new UnauthorizedAccessException();
		}
	}

	private String encodeJson(Map<String, ?> value) {
		try {
			return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8));
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Access Token 생성에 실패했습니다.", exception);
		}
	}

	private String sign(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
			return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("Access Token 서명에 실패했습니다.", exception);
		}
	}

	private boolean signatureMatches(String signatureInput, String actualSignature) {
		try {
			byte[] expected = BASE64_URL_DECODER.decode(sign(signatureInput));
			byte[] actual = BASE64_URL_DECODER.decode(actualSignature);
			return MessageDigest.isEqual(expected, actual);
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}
}
