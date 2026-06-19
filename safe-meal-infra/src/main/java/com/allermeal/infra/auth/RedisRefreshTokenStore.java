package com.allermeal.infra.auth;

import com.allermeal.application.port.out.RefreshTokenStore;
import com.allermeal.application.port.out.command.RefreshTokenCommand;
import com.allermeal.application.port.out.command.RotateRefreshTokenCommand;
import com.allermeal.application.port.out.result.RefreshTokenRotationResult;
import com.allermeal.domain.user.UserId;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

public final class RedisRefreshTokenStore implements RefreshTokenStore {

	static final String ACTIVE_KEY_PREFIX = "refresh-token:v2:active:";
	static final String USED_KEY_PREFIX = "refresh-token:v2:used:";
	static final String FAMILY_KEY_PREFIX = "refresh-token:v2:family:";
	static final String REVOKED_FAMILY_KEY_PREFIX = "refresh-token:v2:revoked-family:";
	private static final String VALUE_SEPARATOR = "|";
	private static final RedisScript<String> ROTATE_SCRIPT = RedisScript.of("""
		local activeValue = redis.call('GET', KEYS[1])
		if activeValue then
			local firstSeparator = string.find(activeValue, '|')
			local secondSeparator = string.find(activeValue, '|', firstSeparator + 1)
			local userId = string.sub(activeValue, 1, firstSeparator - 1)
			local familyId = string.sub(activeValue, firstSeparator + 1, secondSeparator - 1)
			if redis.call('GET', KEYS[5] .. familyId) then
				redis.call('DEL', KEYS[1])
				redis.call('DEL', KEYS[4] .. familyId)
				return 'REUSED'
			end
			local activeTokenHash = redis.call('GET', KEYS[4] .. familyId)
			if activeTokenHash and activeTokenHash ~= ARGV[1] then
				redis.call('DEL', KEYS[1])
				return 'MISSING'
			end
			local oldTtl = redis.call('TTL', KEYS[1])
			if oldTtl <= 0 then
				oldTtl = tonumber(ARGV[3])
			end
			redis.call('DEL', KEYS[1])
			redis.call('SET', KEYS[2], familyId, 'EX', oldTtl)
			redis.call('SET', KEYS[3], userId .. '|' .. familyId .. '|' .. ARGV[2], 'EX', tonumber(ARGV[3]))
			redis.call('SET', KEYS[4] .. familyId, ARGV[4], 'EX', tonumber(ARGV[3]))
			return 'ROTATED|' .. userId
		end
		local usedFamilyId = redis.call('GET', KEYS[2])
		if usedFamilyId then
			local activeTokenHash = redis.call('GET', KEYS[4] .. usedFamilyId)
			if activeTokenHash then
				redis.call('DEL', KEYS[6] .. activeTokenHash)
			end
			redis.call('DEL', KEYS[4] .. usedFamilyId)
			local usedTtl = redis.call('TTL', KEYS[2])
			if usedTtl <= 0 then
				usedTtl = tonumber(ARGV[3])
			end
			redis.call('SET', KEYS[5] .. usedFamilyId, '1', 'EX', usedTtl)
			return 'REUSED'
		end
		return 'MISSING'
		""", String.class);
	private static final RedisScript<String> REVOKE_SCRIPT = RedisScript.of("""
		local activeValue = redis.call('GET', KEYS[1])
		if not activeValue then
			return 'MISSING'
		end
		local firstSeparator = string.find(activeValue, '|')
		local secondSeparator = string.find(activeValue, '|', firstSeparator + 1)
		local familyId = string.sub(activeValue, firstSeparator + 1, secondSeparator - 1)
		local activeTokenHash = redis.call('GET', KEYS[2] .. familyId)
		redis.call('DEL', KEYS[1])
		if activeTokenHash and activeTokenHash == ARGV[1] then
			redis.call('DEL', KEYS[2] .. familyId)
		end
		return 'REVOKED'
		""", String.class);

	private final StringRedisTemplate redisTemplate;

	public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void store(RefreshTokenCommand command) {
		redisTemplate.opsForValue().set(
			ACTIVE_KEY_PREFIX + command.tokenHash(),
			value(command.userId(), command.familyId(), command.expiresAt().toString()),
			command.ttl());
		redisTemplate.opsForValue().set(
			FAMILY_KEY_PREFIX + command.familyId(),
			command.tokenHash(),
			command.ttl());
	}

	@Override
	public RefreshTokenRotationResult rotate(RotateRefreshTokenCommand command) {
		String result = redisTemplate.execute(
			ROTATE_SCRIPT,
			List.of(
				ACTIVE_KEY_PREFIX + command.oldTokenHash(),
				USED_KEY_PREFIX + command.oldTokenHash(),
				ACTIVE_KEY_PREFIX + command.newTokenHash(),
				FAMILY_KEY_PREFIX,
				REVOKED_FAMILY_KEY_PREFIX,
				ACTIVE_KEY_PREFIX),
			command.oldTokenHash(),
			command.expiresAt().toString(),
			Long.toString(seconds(command.ttl())),
			command.newTokenHash());
		if (result == null || result.equals("MISSING")) {
			return RefreshTokenRotationResult.missing();
		}
		if (result.equals("REUSED")) {
			return RefreshTokenRotationResult.reused();
		}
		if (result.startsWith("ROTATED|")) {
			return RefreshTokenRotationResult.rotated(
				new UserId(UUID.fromString(result.substring("ROTATED|".length()))));
		}
		return RefreshTokenRotationResult.missing();
	}

	@Override
	public void revoke(String tokenHash) {
		redisTemplate.execute(
			REVOKE_SCRIPT,
			List.of(ACTIVE_KEY_PREFIX + tokenHash, FAMILY_KEY_PREFIX),
			tokenHash);
	}

	private String value(UserId userId, String familyId, String expiresAt) {
		return userId.value() + VALUE_SEPARATOR + familyId + VALUE_SEPARATOR + expiresAt;
	}

	private long seconds(Duration ttl) {
		return Math.max(1L, ttl.toSeconds());
	}
}
