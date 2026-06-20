package com.allermeal.infra.auth;

import com.allermeal.application.port.out.PasswordResetTokenStore;
import com.allermeal.application.port.out.command.PasswordResetTokenCommand;
import com.allermeal.domain.user.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisPasswordResetTokenStore implements PasswordResetTokenStore {

	static final String KEY_PREFIX = "password-reset:v1:";
	static final String TOKEN_KEY_PREFIX = "password-reset-token:v1:";
	private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>("""
		local userId = redis.call('GET', KEYS[1])
		if not userId then
			return nil
		end
		local userKey = ARGV[2] .. userId
		local storedTokenHash = redis.call('GET', userKey)
		if storedTokenHash ~= ARGV[1] then
			redis.call('DEL', KEYS[1])
			return nil
		end
		redis.call('DEL', KEYS[1])
		redis.call('DEL', userKey)
		return userId
		""", String.class);

	private final StringRedisTemplate redisTemplate;

	public RedisPasswordResetTokenStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void store(PasswordResetTokenCommand command) {
		String userKey = KEY_PREFIX + command.userId().value();
		String tokenKey = TOKEN_KEY_PREFIX + command.tokenHash();
		String previousTokenHash = redisTemplate.opsForValue().get(userKey);
		if (previousTokenHash != null) {
			redisTemplate.delete(TOKEN_KEY_PREFIX + previousTokenHash);
		}
		redisTemplate.opsForValue().set(userKey, command.tokenHash(), command.ttl());
		redisTemplate.opsForValue().set(tokenKey, command.userId().value().toString(), command.ttl());
	}

	@Override
	public Optional<UserId> consume(String tokenHash) {
		String tokenKey = TOKEN_KEY_PREFIX + tokenHash;
		String userIdValue = redisTemplate.execute(CONSUME_SCRIPT, List.of(tokenKey), tokenHash, KEY_PREFIX);
		if (userIdValue == null) {
			return Optional.empty();
		}
		return Optional.of(new UserId(UUID.fromString(userIdValue)));
	}
}
