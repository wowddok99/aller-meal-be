package com.allermeal.infra.auth;

import com.allermeal.application.port.out.RefreshTokenStore;
import com.allermeal.application.port.out.command.RefreshTokenCommand;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class RedisRefreshTokenStore implements RefreshTokenStore {

	static final String KEY_PREFIX = "refresh-token:v1:";

	private final StringRedisTemplate redisTemplate;

	public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void store(RefreshTokenCommand command) {
		redisTemplate.opsForValue().set(
			KEY_PREFIX + command.tokenHash(),
			command.userId().value() + ":" + command.expiresAt().toString(),
			command.ttl());
	}
}
