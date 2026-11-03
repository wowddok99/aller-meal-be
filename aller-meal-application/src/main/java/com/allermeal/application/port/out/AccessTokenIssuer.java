package com.allermeal.application.port.out;

import com.allermeal.application.auth.AccessTokenClaims;
import com.allermeal.domain.user.User;
import java.time.Duration;
import java.time.Instant;

public interface AccessTokenIssuer {

	String issue(User user, Duration ttl);

	default String issueUntil(User user, Instant expiresAt) {
		throw new UnsupportedOperationException("절대 만료 시각을 사용하는 Access Token 발급을 지원하지 않습니다.");
	}

	default String issueUntil(User user, Instant expiresAt, String sessionId) {
		throw new UnsupportedOperationException("세션 식별자를 사용하는 Access Token 발급을 지원하지 않습니다.");
	}

	AccessTokenClaims verify(String token);
}
