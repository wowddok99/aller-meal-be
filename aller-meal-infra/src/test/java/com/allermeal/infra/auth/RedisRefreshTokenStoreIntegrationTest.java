package com.allermeal.infra.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.allermeal.application.port.out.command.RefreshTokenCommand;
import com.allermeal.application.port.out.command.RotateRefreshTokenCommand;
import com.allermeal.application.port.out.result.RefreshTokenRotationStatus;
import com.allermeal.domain.user.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class RedisRefreshTokenStoreIntegrationTest {
	private static final Duration TTL = Duration.ofMinutes(5);
	private String containerId;
	private LettuceConnectionFactory connection;
	private StringRedisTemplate redis;
	private RedisRefreshTokenStore store;

	@BeforeAll
	void startIsolatedRedis() throws Exception {
		try {
			containerId = docker("run", "--rm", "-d", "-p", "127.0.0.1::6379", "redis:8.0.2-alpine").trim();
			String mapped = docker("port", containerId, "6379/tcp").trim();
			int port = Integer.parseInt(mapped.substring(mapped.lastIndexOf(':') + 1));
			connection = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", port));
			connection.afterPropertiesSet();
			connection.start();
			redis = new StringRedisTemplate(connection);
			redis.afterPropertiesSet();
			store = new RedisRefreshTokenStore(redis);
			awaitRedis();
		} catch (Exception | AssertionError failure) {
			cleanup();
			throw failure;
		}
	}

	private void awaitRedis() throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		RuntimeException lastFailure = null;
		while (System.nanoTime() < deadline) {
			try (var redisConnection = connection.getConnection()) {
				if ("PONG".equals(redisConnection.ping())) return;
			} catch (RuntimeException failure) {
				lastFailure = failure;
			}
			Thread.sleep(100);
		}
		throw new IllegalStateException("격리 Redis가 제한 시간 안에 준비되지 않았습니다.", lastFailure);
	}

	@AfterAll
	void cleanup() throws Exception {
		if (connection != null) { connection.destroy(); connection = null; }
		if (containerId != null) {
			String id = containerId;
			containerId = null;
			docker("stop", id);
		}
	}

	@Test
	void concurrentReservationsAllowOneWinner() throws Exception {
		String token = newToken();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch ready = new CountDownLatch(2);
			CountDownLatch start = new CountDownLatch(1);
			Callable<String> first = () -> { ready.countDown(); start.await(); return store.reserve(token, random()).status(); };
			Future<String> a = executor.submit(first);
			Future<String> b = executor.submit(first);
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			start.countDown();
			assertEquals(List.of("BUSY", "RESERVED"), java.util.stream.Stream.of(a.get(5, TimeUnit.SECONDS),
				b.get(5, TimeUnit.SECONDS)).sorted().toList());
		} finally { executor.shutdownNow(); }
	}

	@Test
	void cancelledOrExpiredReservationCannotExecuteLater() throws Exception {
		String token = newToken();
		String operation = random();
		assertEquals("RESERVED", store.reserve(token, operation).status());
		assertEquals("CANCELLED", store.cancel(token, operation));
		assertEquals(RefreshTokenRotationStatus.OPERATION_EXPIRED, rotate(token, random(), operation));
		assertNotNull(store.lookup(token));
		String nextOperation = random();
		assertEquals("RESERVED", store.reserve(token, nextOperation).status());
		redis.expire(RedisRefreshTokenStore.OPERATION_KEY_PREFIX + nextOperation, Duration.ofMillis(1));
		Thread.sleep(30);
		assertEquals(RefreshTokenRotationStatus.OPERATION_EXPIRED, rotate(token, random(), nextOperation));
		assertNotNull(store.lookup(token));
	}

	@Test
	void replayAfterRotationRevokesFamilyAndCancellationDoesNotReturnNewToken() {
		String oldToken = newToken();
		String nextToken = random();
		String operation = random();
		assertEquals("RESERVED", store.reserve(oldToken, operation).status());
		assertEquals(RefreshTokenRotationStatus.ROTATED, rotate(oldToken, nextToken, operation));
		assertEquals("ROTATED", store.cancel(oldToken, operation));
		assertNotNull(store.lookup(nextToken));
		assertEquals(RefreshTokenRotationStatus.REUSED, rotate(oldToken, random(), operation));
		assertNull(store.lookup(nextToken));
	}

	@Test
	void logoutWithUsedTokenRevokesCurrentFamilyAndExpiredTokenIsInactive() throws Exception {
		String oldToken = newToken();
		String nextToken = random();
		String operation = random();
		assertEquals("RESERVED", store.reserve(oldToken, operation).status());
		assertEquals(RefreshTokenRotationStatus.ROTATED, rotate(oldToken, nextToken, operation));
		store.revoke(oldToken);
		assertNull(store.lookup(nextToken));
		assertEquals("UNAUTHORIZED", store.cancel(oldToken, operation));
		String expiring = newToken();
		redis.expire(RedisRefreshTokenStore.ACTIVE_KEY_PREFIX + expiring, Duration.ofMillis(1));
		Thread.sleep(30);
		assertNull(store.lookup(expiring));
		assertEquals("MISSING", store.reserve(expiring, random()).status());
	}

	private String newToken() {
		UserId user = new UserId(UUID.randomUUID());
		String token = random();
		Instant now = Instant.now();
		var snapshot = store.loginSnapshot(user);
		store.store(new RefreshTokenCommand(user, "n" + random(), token, now, now.plus(TTL), 0,
			TTL, true, now.plus(Duration.ofDays(30)), snapshot));
		return token;
	}

	private RefreshTokenRotationStatus rotate(String from, String to, String operation) {
		Instant now = Instant.now();
		return store.rotate(new RotateRefreshTokenCommand(from, to, now.plus(TTL), TTL, now, TTL, operation)).status();
	}

	private String random() { return UUID.randomUUID().toString().replace("-", ""); }

	private String docker(String... arguments) throws Exception {
		var command = new java.util.ArrayList<String>();
		command.add("docker");
		command.addAll(List.of(arguments));
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		if (!process.waitFor(90, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new IllegalStateException("Docker 명령이 시간 초과되었습니다.");
		}
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (process.exitValue() != 0) throw new IllegalStateException("격리 Redis Docker 명령에 실패했습니다: " + output);
		return output;
	}
}
