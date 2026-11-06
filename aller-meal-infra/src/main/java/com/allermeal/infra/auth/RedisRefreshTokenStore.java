package com.allermeal.infra.auth;

import com.allermeal.application.auth.RefreshSessionPolicy;
import com.allermeal.application.auth.UnauthorizedAccessException;
import com.allermeal.application.port.out.RefreshTokenStore;
import com.allermeal.application.port.out.command.RefreshTokenCommand;
import com.allermeal.application.port.out.command.RotateRefreshTokenCommand;
import com.allermeal.application.port.out.result.RefreshTokenRotationResult;
import com.allermeal.application.port.out.result.RefreshSessionLookupResult;
import com.allermeal.application.port.out.result.RefreshOperationReservationResult;
import com.allermeal.application.port.out.result.RefreshTokenLoginSnapshotResult;
import com.allermeal.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

public final class RedisRefreshTokenStore implements RefreshTokenStore {

	static final String OPERATION_KEY_PREFIX = "refresh-token:v2:operation:";
	static final String OPERATION_FAMILY_KEY_PREFIX = "refresh-token:v2:operation-family:";
	static final String ACTIVE_KEY_PREFIX = "refresh-token:v2:active:";
	static final String USED_KEY_PREFIX = "refresh-token:v2:used:";
	static final String POLICY_KEY_PREFIX = "refresh-token:v2:policy:";
	static final String FAMILY_KEY_PREFIX = "refresh-token:v2:family:";
	static final String REVOKED_FAMILY_KEY_PREFIX = "refresh-token:v2:revoked-family:";
	static final String USER_TOKEN_SET_KEY_PREFIX = "refresh-token:v2:user-tokens:";
	static final String USER_REVOKED_AFTER_KEY_PREFIX = "refresh-token:v2:user-revoked-after:";
	static final String USER_REVOCATION_GENERATION_KEY_PREFIX = "refresh-token:v2:user-revocation-generation:";
	private static final String ACTIVE_LOOKUP = """
		local value = redis.call('GET', KEYS[1])
		if not value or redis.call('PTTL', KEYS[1]) <= 0 then return nil end
		local userId, familyId, issuedAt, expiry, version = string.match(value, '^([^|]+)|([^|]+)|([^|]+)|([^|]+)|([^|]+)$')
		if not userId or not tonumber(issuedAt) then return nil end
		if string.sub(familyId, 1, 1) == 'n' and string.sub(version, 1, 1) == 'n' then version = string.sub(version, 2) end
		if not tonumber(version) or tonumber(version) < 0 then return nil end
		local revoked = redis.call('GET', KEYS[3] .. userId)
		if revoked and tonumber(issuedAt) <= tonumber(revoked) then return nil end
		if redis.call('GET', KEYS[4] .. familyId) then return nil end
		if redis.call('GET', KEYS[2] .. familyId) ~= ARGV[1] then return nil end
		if string.sub(familyId, 1, 1) == 'n' then
			local policy = redis.call('GET', KEYS[5] .. familyId)
			local mode, deadline
			if policy then mode, deadline = string.match(policy, '^([01])|(%d+)$') end
			local now = redis.call('TIME')
			if not mode or tonumber(deadline) <= now[1] * 1000 + math.floor(now[2] / 1000) then return nil end
		end
		return {userId, familyId, version}
		""";
	private static final RedisScript<String> LOOKUP_SCRIPT = RedisScript.of("local function lookup()\n" + ACTIVE_LOOKUP + "end\n"
		+ "local v = lookup() if not v then return 'MISSING' end return table.concat(v, '|')", String.class);
	private static final RedisScript<String> RESERVE_SCRIPT = RedisScript.of("local function lookup()\n" + ACTIVE_LOOKUP + "end\n" + """
		local v = lookup()
		if not v then return 'MISSING' end
		local pointer = KEYS[6] .. v[2]
		if redis.call('EXISTS', pointer) == 1 then return 'BUSY' end
		local ttl = math.min(30000, redis.call('PTTL', KEYS[1]))
		if ttl <= 0 then return 'MISSING' end
		local now = redis.call('TIME')
		local deadline = now[1] * 1000 + math.floor(now[2] / 1000) + ttl
		if not redis.call('SET', KEYS[7], v[2] .. '|' .. ARGV[1] .. '|P', 'PX', ttl, 'NX') then return 'BUSY' end
		redis.call('SET', pointer, ARGV[2], 'PX', ttl)
		return 'RESERVED|' .. v[2] .. '|' .. string.format('%.0f', deadline)
		""", String.class);
	private static final RedisScript<String> CANCEL_SCRIPT = RedisScript.of("""
		local op = redis.call('GET', KEYS[1])
		local active = redis.call('GET', KEYS[2])
		local family = active and string.match(active, '^[^|]+|([^|]+)|')
		local usedFamily = redis.call('GET', KEYS[3])
		if not op then
			if not family or redis.call('GET', KEYS[4] .. family) ~= ARGV[1] then return 'UNAUTHORIZED' end
			return 'EXPIRED'
		end
		local opFamily, opHash, state = string.match(op, '^([^|]+)|([^|]+)|([PCR])$')
		if not opFamily then return 'UNAUTHORIZED' end
		local currentHash = redis.call('GET', KEYS[4] .. opFamily)
		local current = currentHash and redis.call('GET', KEYS[6] .. currentHash)
		if not current or not string.match(current, '^[^|]+|' .. opFamily .. '|')
			or redis.call('GET', KEYS[7] .. opFamily) then return 'UNAUTHORIZED' end
		if not ((family == opFamily and redis.call('GET', KEYS[4] .. family) == ARGV[1])
			or (usedFamily == opFamily and opHash == ARGV[1])) then return 'UNAUTHORIZED' end
		if state == 'R' then return 'ROTATED' end
		if state == 'C' then return 'CANCELLED' end
		redis.call('SET', KEYS[1], opFamily .. '|' .. opHash .. '|C', 'KEEPTTL')
		if redis.call('GET', KEYS[5] .. opFamily) == ARGV[2] then redis.call('DEL', KEYS[5] .. opFamily) end
		return 'CANCELLED'
		""", String.class);
	private static final RedisScript<String> LOGIN_SNAPSHOT_SCRIPT = RedisScript.of("""
		local now = redis.call('TIME')
		local startedAt = now[1] * 1000 + math.floor(now[2] / 1000)
		local revokedAfter = redis.call('GET', KEYS[1]) or '-'
		if revokedAfter ~= '-' and startedAt <= tonumber(revokedAfter) then
			startedAt = tonumber(revokedAfter) + 1
		end
		return string.format('%.0f', startedAt) .. '|' .. (redis.call('GET', KEYS[2]) or '-') .. '|' .. revokedAfter
		""", String.class);
	// 신규 세션 버전에 접두사를 붙여 구버전 서버가 정책을 무시한 채 회전하지 못하게 한다.
	private static final RedisScript<String> STORE_SCRIPT = RedisScript.of("""
		if (redis.call('GET', KEYS[5]) or '-') ~= ARGV[11]
			or (redis.call('GET', KEYS[6]) or '-') ~= ARGV[12] then
			return 'REVOKED'
		end
		local now = redis.call('TIME')
		local remainingTtl = math.floor((tonumber(ARGV[3]) + tonumber(ARGV[7]) * 1000
			- now[1] * 1000 - math.floor(now[2] / 1000)) / 1000)
		if remainingTtl <= 0 then
			return 'EXPIRED'
		end
		local storedVersion = ARGV[8] == '' and ARGV[5] or 'n' .. ARGV[5]
		redis.call('SET', KEYS[1], ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3] .. '|' .. ARGV[4] .. '|' .. storedVersion, 'EX', remainingTtl)
		redis.call('SET', KEYS[2], ARGV[6], 'EX', remainingTtl)
		redis.call('SADD', KEYS[3], ARGV[6])
		local setTtl = redis.call('TTL', KEYS[3])
		if setTtl < remainingTtl then
			redis.call('EXPIRE', KEYS[3], remainingTtl)
		end
		if ARGV[8] ~= '' then
			redis.call('SET', KEYS[4], ARGV[8] .. '|' .. ARGV[9], 'EX', tonumber(ARGV[10]))
		end
		return 'STORED'
		""", String.class);
	private static final RedisScript<String> ROTATE_SCRIPT = RedisScript.of("""
		local activeValue = redis.call('GET', KEYS[1])
		if activeValue then
			local oldTtl = redis.call('PTTL', KEYS[1])
			if oldTtl <= 0 then
				return 'MISSING'
			end
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
			local newFamily = string.sub(familyId, 1, 1) == 'n'
			if newFamily and string.sub(sessionVersion, 1, 1) == 'n' then
				sessionVersion = string.sub(sessionVersion, 2)
			end
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
			local newTtl = tonumber(ARGV[3])
			local policy = ''
			local absoluteMs = ''
			local newExpiryMs = ''
			if newFamily then
				local metadata = redis.call('GET', KEYS[9] .. familyId)
				local separator = metadata and string.find(metadata, '|')
				if not separator then
					return 'MISSING'
				end
				policy = string.sub(metadata, 1, separator - 1)
				absoluteMs = string.sub(metadata, separator + 1)
				local deadline = tonumber(absoluteMs)
				local now = tonumber(ARGV[5])
				if (policy ~= '0' and policy ~= '1') or not deadline or not now or deadline <= now then
					return 'MISSING'
				end
				newTtl = math.min(tonumber(ARGV[6]), math.floor((deadline - now) / 1000))
				if newTtl <= 0 then
					return 'MISSING'
				end
				newExpiryMs = tostring(now + newTtl * 1000)
			end
			if ARGV[7] ~= '' then
				local operation = redis.call('GET', KEYS[10] .. ARGV[7])
				if operation ~= familyId .. '|' .. ARGV[1] .. '|P'
					or redis.call('GET', KEYS[11] .. familyId) ~= ARGV[7] then
					return 'OPERATION_EXPIRED'
				end
				redis.call('SET', KEYS[10] .. ARGV[7], familyId .. '|' .. ARGV[1] .. '|R', 'KEEPTTL')
				redis.call('DEL', KEYS[11] .. familyId)
			end
			redis.call('DEL', KEYS[1])
			redis.call('SET', KEYS[2], familyId, 'PX', oldTtl)
			local storedVersion = newFamily and 'n' .. sessionVersion or sessionVersion
			redis.call('SET', KEYS[3], userId .. '|' .. familyId .. '|' .. issuedAt .. '|' .. ARGV[2] .. '|' .. storedVersion, 'EX', newTtl)
			redis.call('SET', KEYS[4] .. familyId, ARGV[4], 'EX', newTtl)
			redis.call('SREM', KEYS[7] .. userId, ARGV[1])
			redis.call('SADD', KEYS[7] .. userId, ARGV[4])
			local setTtl = redis.call('TTL', KEYS[7] .. userId)
			if setTtl < newTtl then
				redis.call('EXPIRE', KEYS[7] .. userId, newTtl)
			end
			return 'ROTATED|' .. userId .. '|' .. sessionVersion .. '|' .. policy .. '|' .. absoluteMs .. '|' .. newExpiryMs .. '|' .. familyId
		end
		local usedFamilyId = redis.call('GET', KEYS[2])
		if usedFamilyId then
			local usedTtl = redis.call('PTTL', KEYS[2])
			if usedTtl <= 0 then
				return 'MISSING'
			end
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
			redis.call('SET', KEYS[5] .. usedFamilyId, '1', 'PX', usedTtl)
			return 'REUSED'
		end
		return 'MISSING'
		""", String.class);
	private static final RedisScript<String> REVOKE_SCRIPT = RedisScript.of("""
		local activeValue = redis.call('GET', KEYS[1])
		if not activeValue then
			local usedFamilyId = redis.call('GET', KEYS[4])
			if not usedFamilyId or redis.call('PTTL', KEYS[4]) <= 0 then
				return 'MISSING'
			end
			if ARGV[2] ~= '' and usedFamilyId ~= ARGV[2] then return 'MISMATCH' end
			local activeTokenHash = redis.call('GET', KEYS[2] .. usedFamilyId)
			if activeTokenHash then
				local currentValue = redis.call('GET', KEYS[5] .. activeTokenHash)
				if currentValue then
					local firstSeparator = string.find(currentValue, '|')
					local secondSeparator = firstSeparator and string.find(currentValue, '|', firstSeparator + 1)
					if secondSeparator and string.sub(currentValue, firstSeparator + 1, secondSeparator - 1) == usedFamilyId then
						local userId = string.sub(currentValue, 1, firstSeparator - 1)
						redis.call('SREM', KEYS[3] .. userId, activeTokenHash)
						redis.call('DEL', KEYS[5] .. activeTokenHash)
					end
				end
			end
			redis.call('DEL', KEYS[2] .. usedFamilyId)
			return 'REVOKED'
		end
		if redis.call('PTTL', KEYS[1]) <= 0 then
			return 'MISSING'
		end
		local firstSeparator = string.find(activeValue, '|')
		local secondSeparator = string.find(activeValue, '|', firstSeparator + 1)
		local familyId = string.sub(activeValue, firstSeparator + 1, secondSeparator - 1)
		if ARGV[2] ~= '' and familyId ~= ARGV[2] then return 'MISMATCH' end
		local activeTokenHash = redis.call('GET', KEYS[2] .. familyId)
		redis.call('DEL', KEYS[1])
		local userId = string.sub(activeValue, 1, firstSeparator - 1)
		redis.call('SREM', KEYS[3] .. userId, ARGV[1])
		if activeTokenHash and activeTokenHash == ARGV[1] then
			redis.call('DEL', KEYS[2] .. familyId)
		end
		return 'REVOKED'
		""", String.class);
	private static final RedisScript<String> LOGOUT_FAMILY_SCRIPT = RedisScript.of("""
		local active = redis.call('GET', KEYS[1])
		if active and redis.call('PTTL', KEYS[1]) > 0 then
			return string.match(active, '^[^|]+|([^|]+)|')
		end
		if redis.call('PTTL', KEYS[2]) > 0 then return redis.call('GET', KEYS[2]) end
		return nil
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
		redis.call('INCR', KEYS[5])
		redis.call('EXPIRE', KEYS[5], tonumber(ARGV[1]))
		redis.call('DEL', KEYS[1])
		return 'REVOKED'
		""", String.class);

	private final StringRedisTemplate redisTemplate;

	public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public RefreshTokenLoginSnapshotResult loginSnapshot(UserId userId) {
		String snapshot = redisTemplate.execute(LOGIN_SNAPSHOT_SCRIPT,
			List.of(USER_REVOKED_AFTER_KEY_PREFIX + userId.value(), USER_REVOCATION_GENERATION_KEY_PREFIX + userId.value()));
		if (snapshot == null) {
			throw new UnauthorizedAccessException();
		}
		String[] parts = snapshot.split("\\|", -1);
		if (parts.length != 3) {
			throw new UnauthorizedAccessException();
		}
		try {
			return new RefreshTokenLoginSnapshotResult(Instant.ofEpochMilli(Long.parseLong(parts[0])), parts[1], parts[2]);
		} catch (IllegalArgumentException exception) {
			throw new UnauthorizedAccessException();
		}
	}

	@Override
	public void store(RefreshTokenCommand command) {
		String result = redisTemplate.execute(
			STORE_SCRIPT,
			List.of(
				ACTIVE_KEY_PREFIX + command.tokenHash(),
				FAMILY_KEY_PREFIX + command.familyId(),
				USER_TOKEN_SET_KEY_PREFIX + command.userId().value(),
				POLICY_KEY_PREFIX + command.familyId(),
				USER_REVOKED_AFTER_KEY_PREFIX + command.userId().value(),
				USER_REVOCATION_GENERATION_KEY_PREFIX + command.userId().value()),
			command.userId().value().toString(),
			command.familyId(),
			Long.toString(command.loginSnapshot().startedAt().toEpochMilli()),
			command.expiresAt().toString(),
			Long.toString(command.sessionVersion()),
			command.tokenHash(),
			Long.toString(seconds(command.ttl())),
			command.rememberMe() == null ? "" : command.rememberMe() ? "1" : "0",
			command.absoluteExpiresAt() == null ? "" : Long.toString(command.absoluteExpiresAt().toEpochMilli()),
			command.absoluteExpiresAt() == null ? "1" : Long.toString(seconds(Duration.between(command.issuedAt(), command.absoluteExpiresAt()).plus(command.ttl()))),
			command.loginSnapshot().revokedAfter(),
			command.loginSnapshot().generation());
		if (!"STORED".equals(result)) {
			throw new UnauthorizedAccessException();
		}
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
				USER_REVOKED_AFTER_KEY_PREFIX,
				POLICY_KEY_PREFIX, OPERATION_KEY_PREFIX, OPERATION_FAMILY_KEY_PREFIX),
			command.oldTokenHash(),
			command.expiresAt().toString(),
			Long.toString(seconds(command.ttl())),
			command.newTokenHash(),
			Long.toString(command.issuedAt().toEpochMilli()),
			Long.toString(seconds(command.sessionSlidingTtl())),
			command.operationId() == null ? "" : command.operationId());
		if (result == null || result.equals("MISSING")) {
			return RefreshTokenRotationResult.missing();
		}
		if (result.equals("OPERATION_EXPIRED")) {
			return RefreshTokenRotationResult.operationExpired();
		}
		if (result.equals("REUSED")) {
			return RefreshTokenRotationResult.reused();
		}
		if (result.startsWith("ROTATED|")) {
			String[] parts = result.split("\\|", -1);
			if (parts.length == 7 && !parts[1].isBlank() && !parts[2].isBlank()) {
				try {
					long sessionVersion = Long.parseLong(parts[2]);
					if (sessionVersion >= 0) {
						Boolean rememberMe = parts[3].isEmpty() ? null : parts[3].equals("1");
						Instant absoluteExpiresAt = parts[4].isEmpty() ? null : Instant.ofEpochMilli(Long.parseLong(parts[4]));
						Instant refreshExpiresAt = parts[5].isEmpty() ? null : Instant.ofEpochMilli(Long.parseLong(parts[5]));
						return RefreshTokenRotationResult.rotated(new UserId(UUID.fromString(parts[1])), sessionVersion,
							rememberMe, absoluteExpiresAt, refreshExpiresAt, parts[6]);
					}
				} catch (IllegalArgumentException ignored) {
					// A malformed Redis value is an invalid session, never a reason to accept a token.
				}
			}
		}
		return RefreshTokenRotationResult.missing();
	}

	private List<String> lookupKeys(String hash) {
		return List.of(ACTIVE_KEY_PREFIX + hash, FAMILY_KEY_PREFIX, USER_REVOKED_AFTER_KEY_PREFIX,
			REVOKED_FAMILY_KEY_PREFIX, POLICY_KEY_PREFIX);
	}

	@Override
	public RefreshSessionLookupResult lookup(String tokenHash) {
		String value = redisTemplate.execute(LOOKUP_SCRIPT, lookupKeys(tokenHash), tokenHash);
		if (value == null || value.equals("MISSING")) return null;
		String[] parts = value.split("\\|", -1);
		try {
			if (parts.length != 3) return null;
			return new RefreshSessionLookupResult(new UserId(UUID.fromString(parts[0])), Long.parseLong(parts[2]), parts[1]);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	@Override
	public RefreshOperationReservationResult reserve(String tokenHash, String operationId) {
		var keys = new java.util.ArrayList<>(lookupKeys(tokenHash));
		keys.add(OPERATION_FAMILY_KEY_PREFIX);
		keys.add(OPERATION_KEY_PREFIX + operationId);
		String value = redisTemplate.execute(RESERVE_SCRIPT, keys, tokenHash, operationId);
		if (value == null) throw new IllegalStateException("갱신 예약 결과가 없습니다.");
		String[] parts = value.split("\\|", -1);
		if (parts.length == 3 && parts[0].equals("RESERVED")) {
			return new RefreshOperationReservationResult(parts[0], parts[1], Instant.ofEpochMilli(Long.parseLong(parts[2])));
		}
		return new RefreshOperationReservationResult(value, null, null);
	}

	@Override
	public String cancel(String tokenHash, String operationId) {
		String result = redisTemplate.execute(CANCEL_SCRIPT, List.of(OPERATION_KEY_PREFIX + operationId,
			ACTIVE_KEY_PREFIX + tokenHash, USED_KEY_PREFIX + tokenHash, FAMILY_KEY_PREFIX, OPERATION_FAMILY_KEY_PREFIX,
			ACTIVE_KEY_PREFIX, REVOKED_FAMILY_KEY_PREFIX),
			tokenHash, operationId);
		if (result == null) throw new IllegalStateException("갱신 예약 취소 결과가 없습니다.");
		return result;
	}

	@Override
	public void revoke(String tokenHash) {
		redisTemplate.execute(
			REVOKE_SCRIPT,
			List.of(ACTIVE_KEY_PREFIX + tokenHash, FAMILY_KEY_PREFIX, USER_TOKEN_SET_KEY_PREFIX,
				USED_KEY_PREFIX + tokenHash, ACTIVE_KEY_PREFIX),
			tokenHash, "");
	}

	@Override
	public String findLogoutFamily(String tokenHash) {
		return redisTemplate.execute(LOGOUT_FAMILY_SCRIPT,
			List.of(ACTIVE_KEY_PREFIX + tokenHash, USED_KEY_PREFIX + tokenHash));
	}

	@Override
	public boolean revokeIfFamily(String tokenHash, String familyId) {
		String result = redisTemplate.execute(REVOKE_SCRIPT,
			List.of(ACTIVE_KEY_PREFIX + tokenHash, FAMILY_KEY_PREFIX, USER_TOKEN_SET_KEY_PREFIX,
				USED_KEY_PREFIX + tokenHash, ACTIVE_KEY_PREFIX), tokenHash, familyId);
		return "REVOKED".equals(result);
	}

	@Override
	public void revokeAll(UserId userId, Duration ttl) {
		redisTemplate.execute(
			REVOKE_ALL_SCRIPT,
			List.of(
				USER_TOKEN_SET_KEY_PREFIX + userId.value(),
				ACTIVE_KEY_PREFIX,
				FAMILY_KEY_PREFIX,
				USER_REVOKED_AFTER_KEY_PREFIX + userId.value(),
				USER_REVOCATION_GENERATION_KEY_PREFIX + userId.value()),
			Long.toString(seconds(ttl.compareTo(RefreshSessionPolicy.SLIDING_TTL) < 0
				? RefreshSessionPolicy.SLIDING_TTL : ttl)));
	}

	private long seconds(Duration ttl) {
		return Math.max(1L, ttl.toSeconds());
	}
}
