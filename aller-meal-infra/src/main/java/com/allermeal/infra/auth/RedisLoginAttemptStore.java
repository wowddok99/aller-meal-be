package com.allermeal.infra.auth;

import com.allermeal.application.port.out.LoginAttemptStore;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

public final class RedisLoginAttemptStore implements LoginAttemptStore {

	private static final String FAILURE_PREFIX = "login-failure:v1:";
	private static final String LOCK_PREFIX = "login-lock:v1:";
	private static final RedisScript<Long> FAILURE_SCRIPT = RedisScript.of("""
		local count = redis.call('INCR', KEYS[1])
		if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end
		if count >= tonumber(ARGV[2]) then
			redis.call('SET', KEYS[2], '1', 'EX', tonumber(ARGV[3]))
			redis.call('DEL', KEYS[1])
			return 1
		end
		return 0
		""", Long.class);
	private static final RedisScript<Long> SUCCESS_SCRIPT = RedisScript.of("""
		if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
		redis.call('DEL', KEYS[1])
		return 1
		""", Long.class);
	private final StringRedisTemplate redis;
	private final Duration window;
	private final Duration lock;
	private final int maxFailures;

	public RedisLoginAttemptStore(StringRedisTemplate redis, Duration window, Duration lock, int maxFailures) {
		this.redis = redis; this.window = window; this.lock = lock; this.maxFailures = maxFailures;
	}
	@Override public boolean isLocked(String emailHash) { return Boolean.TRUE.equals(redis.hasKey(LOCK_PREFIX + emailHash)); }
	@Override public boolean recordFailure(String emailHash) {
		Long locked = redis.execute(FAILURE_SCRIPT, List.of(FAILURE_PREFIX + emailHash, LOCK_PREFIX + emailHash),
			Long.toString(window.toSeconds()), Integer.toString(maxFailures), Long.toString(lock.toSeconds()));
		return Long.valueOf(1).equals(locked);
	}
	@Override public void clear(String emailHash) { redis.delete(FAILURE_PREFIX + emailHash); }
	@Override public boolean clearIfUnlocked(String emailHash) {
		Long allowed = redis.execute(SUCCESS_SCRIPT, List.of(FAILURE_PREFIX + emailHash, LOCK_PREFIX + emailHash));
		return Long.valueOf(1).equals(allowed);
	}
}
