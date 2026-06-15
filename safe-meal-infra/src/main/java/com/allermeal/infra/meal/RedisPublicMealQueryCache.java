package com.allermeal.infra.meal;

import com.allermeal.application.meal.PublicMealQueryResult;
import com.allermeal.application.meal.PublicMealTarget;
import com.allermeal.application.port.out.PublicMealQueryCache;
import com.allermeal.domain.school.SchoolId;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

public final class RedisPublicMealQueryCache implements PublicMealQueryCache {

	private static final Logger log = LoggerFactory.getLogger(RedisPublicMealQueryCache.class);
	private static final String CACHE_KEY_PREFIX = "public-meal-query:v1:";
	private static final String DISPATCH_KEY_PREFIX = "public-meal-dispatch:v1:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final Duration cacheTtl;
	private final Duration dispatchLockTtl;

	public RedisPublicMealQueryCache(
		StringRedisTemplate redisTemplate,
		ObjectMapper objectMapper,
		Duration cacheTtl,
		Duration dispatchLockTtl
	) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.cacheTtl = cacheTtl;
		this.dispatchLockTtl = dispatchLockTtl;
	}

	@Override
	public Optional<PublicMealQueryResult> find(SchoolId schoolId, LocalDate rangeStart, LocalDate rangeEnd) {
		try {
			String value = redisTemplate.opsForValue().get(cacheKey(schoolId, rangeStart, rangeEnd));
			return value == null
				? Optional.empty()
				: Optional.of(objectMapper.readValue(value, PublicMealQueryResult.class));
		} catch (RuntimeException exception) {
			log.warn("공개 급식 Redis 캐시 조회에 실패해 PostgreSQL 조회로 전환합니다. error={}",
				exception.toString());
			return Optional.empty();
		}
	}

	@Override
	public void put(PublicMealQueryResult result) {
		try {
			redisTemplate.opsForValue().set(
				cacheKey(result.schoolId(), result.rangeStart(), result.rangeEnd()),
				objectMapper.writeValueAsString(result),
				cacheTtl);
		} catch (RuntimeException exception) {
			log.warn("공개 급식 Redis 캐시 저장에 실패했습니다. error={}", exception.toString());
		}
	}

	@Override
	public boolean tryAcquireDispatch(SchoolId schoolId, PublicMealTarget target) {
		try {
			Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
				dispatchKey(schoolId, target), "1", dispatchLockTtl);
			return Boolean.TRUE.equals(acquired);
		} catch (RuntimeException exception) {
			log.debug("공개 급식 Redis dispatch lock에 실패해 PostgreSQL CAS로 전환합니다.", exception);
			return true;
		}
	}

	private String cacheKey(SchoolId schoolId, LocalDate rangeStart, LocalDate rangeEnd) {
		return CACHE_KEY_PREFIX + schoolId.value() + ":" + rangeStart + ":" + rangeEnd;
	}

	private String dispatchKey(SchoolId schoolId, PublicMealTarget target) {
		return DISPATCH_KEY_PREFIX + schoolId.value() + ":" + target.mealDate() + ":" + target.mealType();
	}
}
