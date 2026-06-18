package com.allermeal.infra.auth;

import com.allermeal.application.port.out.EmailVerificationTokenStore;
import com.allermeal.application.port.out.command.EmailVerificationTokenCommand;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class RedisEmailVerificationTokenStore implements EmailVerificationTokenStore {

	static final String KEY_PREFIX = "email-verification:v1:";

	private final StringRedisTemplate redisTemplate;

	public RedisEmailVerificationTokenStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void store(EmailVerificationTokenCommand command) {
		redisTemplate.opsForValue().set(KEY_PREFIX + command.userId().value(), command.tokenHash(), command.ttl());
	}
}
