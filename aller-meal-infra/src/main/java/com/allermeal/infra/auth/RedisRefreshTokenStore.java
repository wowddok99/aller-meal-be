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
	static final String USER_TOKEN_SET_KEY_PREFIX = "refresh-token:v2:user-tokens:";
	static final String USER_REVOKED_AFTER_KEY_PREFIX = "refresh-token:v2:user-revoked-after:";
	private static final RedisScript<String> STORE_SCRIPT = RedisScript.of("""
		redis.call('SET', KEYS[1], ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3] .. '|' .. ARGV[4] .. '|' .. ARGV[5], 'EX', tonumber(ARGV[7]))
		redis.call('SET', KEYS[2], ARGV[6], 'EX', tonumber(ARGV[7]))
		redis.call('SADD', KEYS[3], ARGV[6])
		redis.call('EXPIRE', KEYS[3], tonumber(ARGV[7]))
		return 'STORED'
		""", String.class);
	private static final RedisScript<String> ROTATE_SCRIPT = RedisScript.of("""
		local activeValue = redis.call('GET', KEYS[1])
		if activeValue then
			local firstSeparator = string.find(activeValue, '|')
			local secondSeparator = string.find(activeValue, '|', firstSeparator + 1)
			local userId = string.sub(activeValue, 1, firstSeparator - 1)
			local familyId = string.sub(activeValue, firstSeparator + 1, secondSeparator - 1)
			local thirdSeparator = string.find(activeValue, '|', secondSeparator + 1)
			local fourthSeparator = thirdSeparator and string.find(activeValue, '|', thirdSeparator + 1)
			if not fourthSeparator then
				redis.call('DEL', KEYS[1])
				redis.call('DEL', KEYS[4] .. familyId)
				redis.call('SREM', KEYS[7] .. userId, ARGV[1])
				return 'MISSING'
			end
			local issuedAt = string.sub(activeValue, secondSeparator + 1, thirdSeparator - 1)
			local sessionVersion = string.sub(activeValue, fourthSeparator + 1)
			if issuedAt == '' or sessionVersion == '' or tonumber(sessionVersion) == nil or tonumber(sessionVersion) < 0 then
				redis.call('DEL', KEYS[1])
				redis.call('DEL', KEYS[4] .. familyId)
				redis.call('SREM', KEYS[7] .. userId, ARGV[1])
				return 'MISSING'
			end
			local revokedAfter = redis.call('GET', KEYS[8] .. userId)
			if revokedAfter and tonumber(issuedAt) <= tonumber(revokedAfter) then
				redis.call('DEL', KEYS[1])
				redis.call('DEL', KEYS[4] .. familyId)
				redis.call('SREM', KEYS[7] .. userId, ARGV[1])
				return 'MISSING'
			end
			if redis.call('GET', KEYS[5] .. familyId) then
				redis.call('DEL', KEYS[1])
				redis.call('DEL', KEYS[4] .. familyId)
				redis.call('SREM', KEYS[7] .. userId, ARGV[1])
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
			redis.call('SET', KEYS[3], userId .. '|' .. familyId .. '|' .. issuedAt .. '|' .. ARGV[2] .. '|' .. sessionVersion, 'EX', tonumber(ARGV[3]))
			redis.call('SET', KEYS[4] .. familyId, ARGV[4], 'EX', tonumber(ARGV[3]))
			redis.call('SREM', KEYS[7] .. userId, ARGV[1])
			redis.call('SADD', KEYS[7] .. userId, ARGV[4])
			redis.call('EXPIRE', KEYS[7] .. userId, tonumber(ARGV[3]))
			return 'ROTATED|' .. userId .. '|' .. sessionVersion
		end
		local usedFamilyId = redis.call('GET', KEYS[2])
		if usedFamilyId then
			local activeTokenHash = redis.call('GET', KEYS[4] .. usedFamilyId)
			if activeTokenHash then
				local activeValue = redis.call('GET', KEYS[6] .. activeTokenHash)
				if activeValue then
					local separator = string.find(activeValue, '|')
					local userId = string.sub(activeValue, 1, separator - 1)
					redis.call('SREM', KEYS[7] .. userId, activeTokenHash)
				end
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
		local userId = string.sub(activeValue, 1, firstSeparator - 1)
		redis.call('SREM', KEYS[3] .. userId, ARGV[1])
		if activeTokenHash and activeTokenHash == ARGV[1] then
			redis.call('DEL', KEYS[2] .. familyId)
		end
		return 'REVOKED'
		""", String.class);
	private static final RedisScript<String> REVOKE_ALL_SCRIPT = RedisScript.of("""
		local tokenHashes = redis.call('SMEMBERS', KEYS[1])
		for _, tokenHash in ipairs(tokenHashes) do
			local activeKey = KEYS[2] .. tokenHash
			local activeValue = redis.call('GET', activeKey)
			if activeValue then
				local firstSeparator = string.find(activeValue, '|')
				local secondSeparator = string.find(activeValue, '|', firstSeparator + 1)
				local familyId = string.sub(activeValue, firstSeparator + 1, secondSeparator - 1)
				redis.call('DEL', KEYS[3] .. familyId)
			end
			redis.call('DEL', activeKey)
		end
		local now = redis.call('TIME')
		redis.call('SET', KEYS[4], now[1] * 1000 + math.floor(now[2] / 1000), 'EX', tonumber(ARGV[1]))
		redis.call('DEL', KEYS[1])
		return 'REVOKED'
		""", String.class);

	private final StringRedisTemplate redisTemplate;

	public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void store(RefreshTokenCommand command) {
		redisTemplate.execute(
			STORE_SCRIPT,
			List.of(
				ACTIVE_KEY_PREFIX + command.tokenHash(),
				FAMILY_KEY_PREFIX + command.familyId(),
				USER_TOKEN_SET_KEY_PREFIX + command.userId().value()),
			command.userId().value().toString(),
			command.familyId(),
			Long.toString(command.issuedAt().toEpochMilli()),
			command.expiresAt().toString(),
			Long.toString(command.sessionVersion()),
			command.tokenHash(),
			Long.toString(seconds(command.ttl())));
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
				ACTIVE_KEY_PREFIX,
				USER_TOKEN_SET_KEY_PREFIX,
				USER_REVOKED_AFTER_KEY_PREFIX),
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
			String[] parts = result.split("\\|", -1);
			if (parts.length == 3 && !parts[1].isBlank() && !parts[2].isBlank()) {
				try {
					long sessionVersion = Long.parseLong(parts[2]);
					if (sessionVersion >= 0) {
						return RefreshTokenRotationResult.rotated(new UserId(UUID.fromString(parts[1])), sessionVersion);
					}
				} catch (IllegalArgumentException ignored) {
					// A malformed Redis value is an invalid session, never a reason to accept a token.
				}
			}
		}
		return RefreshTokenRotationResult.missing();
	}

	@Override
	public void revoke(String tokenHash) {
		redisTemplate.execute(
			REVOKE_SCRIPT,
			List.of(ACTIVE_KEY_PREFIX + tokenHash, FAMILY_KEY_PREFIX, USER_TOKEN_SET_KEY_PREFIX),
			tokenHash);
	}

	@Override
	public void revokeAll(UserId userId, Duration ttl) {
		redisTemplate.execute(
			REVOKE_ALL_SCRIPT,
			List.of(
				USER_TOKEN_SET_KEY_PREFIX + userId.value(),
				ACTIVE_KEY_PREFIX,
				FAMILY_KEY_PREFIX,
				USER_REVOKED_AFTER_KEY_PREFIX + userId.value()),
			Long.toString(seconds(ttl)));
	}

	private long seconds(Duration ttl) {
		return Math.max(1L, ttl.toSeconds());
	}
}
