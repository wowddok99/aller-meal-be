package com.allermeal.api.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allermeal.api.auth.AuthController;
import com.allermeal.api.auth.AuthCookieWriter;
import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.api.auth.AuthenticationRequestProtectionFilter;
import com.allermeal.api.error.ApiExceptionHandler;
import com.allermeal.api.error.TraceIdFilter;
import com.allermeal.application.account.AccountWithdrawalService;
import com.allermeal.application.auth.AccessTokenClaims;
import com.allermeal.application.auth.RefreshService;
import com.allermeal.application.port.out.AccessTokenIssuer;
import com.allermeal.application.port.out.AccountWithdrawalPrivacyRepository;
import com.allermeal.application.port.out.EmailVerificationTokenHasher;
import com.allermeal.application.port.out.RefreshTokenStore;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.VerificationTokenGenerator;
import com.allermeal.application.port.out.command.RefreshTokenCommand;
import com.allermeal.application.port.out.command.RotateRefreshTokenCommand;
import com.allermeal.application.port.out.result.RefreshTokenRotationResult;
import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.EncryptedEmail;
import com.allermeal.domain.user.PasswordHash;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

final class AccountWithdrawalHttpContractTest {

	private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
	private static final String ORIGIN = "http://localhost:3000";
	private static final String CSRF = "csrf-token";

	@Test
	void activeAndPendingWithdrawalRequestsFollowTheHttpContract() throws Exception {
		Fixture fixture = Fixture.active();

		fixture.mvc().perform(get("/api/v1/account/withdrawal")
			.cookie(accessCookie("valid-access")))
			.andExpect(status().isNoContent());

		MvcResult requested = fixture.mvc().perform(post("/api/v1/account/withdrawal")
			.cookie(accessCookie("valid-access"), csrfCookie(CSRF))
			.header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", CSRF))
			.andExpect(status().isOk())
			.andReturn();
		String requestedBody = requested.getResponse().getContentAsString();
		String requestedAt = jsonStringField(requestedBody, "withdrawalRequestedAt");
		String dueAt = jsonStringField(requestedBody, "withdrawalDueAt");
		assertEquals("3", jsonNumberField(requestedBody, "maskedNotificationCount"));

		MvcResult current = fixture.mvc().perform(get("/api/v1/account/withdrawal")
			.cookie(accessCookie("valid-access")))
			.andExpect(status().isOk())
			.andReturn();
		String currentBody = current.getResponse().getContentAsString();
		assertEquals(requestedAt, jsonStringField(currentBody, "withdrawalRequestedAt"));
		assertEquals(dueAt, jsonStringField(currentBody, "withdrawalDueAt"));
		assertEquals("3", jsonNumberField(currentBody, "maskedNotificationCount"));

		MvcResult conflict = fixture.mvc().perform(post("/api/v1/account/withdrawal")
			.cookie(accessCookie("valid-access"), csrfCookie(CSRF))
			.header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", CSRF))
			.andExpect(status().isConflict())
			.andReturn();
		assertTrue(conflict.getResponse().getContentAsString().contains("ACCOUNT_WITHDRAWAL_CONFLICT"));

		fixture.mvc().perform(delete("/api/v1/account/withdrawal")
			.cookie(accessCookie("valid-access"), csrfCookie(CSRF))
			.header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", CSRF))
			.andExpect(status().isNoContent());

		fixture.mvc().perform(get("/api/v1/account/withdrawal")
			.cookie(accessCookie("valid-access")))
			.andExpect(status().isNoContent());

		MvcResult activeDelete = fixture.mvc().perform(delete("/api/v1/account/withdrawal")
			.cookie(accessCookie("valid-access"), csrfCookie(CSRF))
			.header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", CSRF))
			.andExpect(status().isConflict())
			.andReturn();
		assertTrue(activeDelete.getResponse().getContentAsString().contains("ACCOUNT_WITHDRAWAL_CONFLICT"));
	}

	@Test
	void withdrawalRejectsMissingAuthenticationAndCookieWritesWithoutValidCsrfOrigin() throws Exception {
		Fixture fixture = Fixture.active();

		MvcResult unauthenticated = fixture.mvc().perform(get("/api/v1/account/withdrawal"))
			.andExpect(status().isUnauthorized())
			.andReturn();
		assertTrue(unauthenticated.getResponse().getContentAsString().contains("\"code\":\"UNAUTHORIZED\""));

		MvcResult csrfRejected = fixture.mvc().perform(post("/api/v1/account/withdrawal")
			.cookie(accessCookie("valid-access"), csrfCookie(CSRF)))
			.andExpect(status().isForbidden())
			.andReturn();
		assertTrue(csrfRejected.getResponse().getContentAsString().contains("CSRF_VALIDATION_FAILED"));

		MvcResult originRejected = fixture.mvc().perform(post("/api/v1/account/withdrawal")
			.cookie(accessCookie("valid-access"), csrfCookie(CSRF))
			.header(HttpHeaders.ORIGIN, "https://untrusted.example")
			.header("X-CSRF-Token", CSRF))
			.andExpect(status().isForbidden())
			.andReturn();
		assertTrue(originRejected.getResponse().getContentAsString().contains("CSRF_VALIDATION_FAILED"));
	}

	@Test
	void expiredAccessCanRefreshThenReadAndCancelPendingWithdrawal() throws Exception {
		Fixture fixture = Fixture.pending(NOW.plus(Duration.ofDays(7)));

		fixture.mvc().perform(get("/api/v1/account/withdrawal")
			.cookie(accessCookie("expired-access")))
			.andExpect(status().isUnauthorized());

		MvcResult refreshed = fixture.mvc().perform(post("/api/v1/auth/refresh")
			.cookie(refreshCookie("pending-refresh"), csrfCookie(CSRF))
			.header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", CSRF))
			.andExpect(status().isOk())
			.andReturn();
		assertEquals(3, refreshed.getResponse().getHeaders(HttpHeaders.SET_COOKIE).size());
		String newAccessToken = cookieValue(refreshed, AuthCookieWriter.ACCESS_TOKEN_COOKIE);
		String newRefreshToken = cookieValue(refreshed, AuthCookieWriter.REFRESH_TOKEN_COOKIE);
		String newCsrfToken = cookieValue(refreshed, AuthCookieWriter.CSRF_TOKEN_COOKIE);

		fixture.mvc().perform(get("/api/v1/account/withdrawal")
			.cookie(accessCookie(newAccessToken)))
			.andExpect(status().isOk());

		fixture.mvc().perform(delete("/api/v1/account/withdrawal")
			.cookie(accessCookie(newAccessToken), refreshCookie(newRefreshToken), csrfCookie(newCsrfToken))
			.header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", newCsrfToken))
			.andExpect(status().isNoContent());
	}

	@Test
	void refreshRejectsWithdrawalPendingUserAfterGracePeriod() throws Exception {
		Fixture fixture = Fixture.pending(NOW.minusSeconds(1));

		MvcResult response = fixture.mvc().perform(post("/api/v1/auth/refresh")
			.cookie(refreshCookie("pending-refresh"), csrfCookie(CSRF))
			.header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", CSRF))
			.andExpect(status().isUnauthorized())
			.andReturn();
		assertTrue(response.getResponse().getContentAsString().contains("\"code\":\"UNAUTHORIZED\""));
	}

	private static Cookie accessCookie(String value) {
		return new Cookie(AuthCookieWriter.ACCESS_TOKEN_COOKIE, value);
	}

	private static Cookie refreshCookie(String value) {
		return new Cookie(AuthCookieWriter.REFRESH_TOKEN_COOKIE, value);
	}

	private static Cookie csrfCookie(String value) {
		return new Cookie(AuthCookieWriter.CSRF_TOKEN_COOKIE, value);
	}

	private static String cookieValue(MvcResult result, String name) {
		return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
			.filter(value -> value.startsWith(name + "="))
			.findFirst()
			.map(value -> value.substring(name.length() + 1, value.indexOf(';')))
			.orElseThrow();
	}

	private static String jsonStringField(String body, String fieldName) {
		return jsonField(body, fieldName, "\\\"([^\\\"]+)\\\"");
	}

	private static String jsonNumberField(String body, String fieldName) {
		return jsonField(body, fieldName, "(\\d+)");
	}

	private static String jsonField(String body, String fieldName, String valuePattern) {
		Matcher matcher = Pattern.compile("\\\"" + fieldName + "\\\":" + valuePattern).matcher(body);
		if (!matcher.find()) {
			throw new AssertionError(fieldName + " 필드가 응답에 없습니다.");
		}
		return matcher.group(1);
	}

	private static final class Fixture {

		private final InMemoryUserRepository userRepository;
		private final InMemoryAccessTokenIssuer accessTokenIssuer;
		private final MockMvc mvc;

		private Fixture(User user) {
			userRepository = new InMemoryUserRepository(user);
			accessTokenIssuer = new InMemoryAccessTokenIssuer();
			accessTokenIssuer.allow("valid-access", user);
			AccountWithdrawalService withdrawalService = new AccountWithdrawalService(
				userRepository,
				new AccountWithdrawalPrivacyRepository() {
					@Override
					public int maskNotificationPersonalData(UserId userId, Instant maskedAt) {
						return 3;
					}

					@Override
					public int deleteExpiredPersonalData(Instant dueBeforeInclusive, Instant deletedAt) {
						return 0;
					}
				},
				CLOCK);
			RefreshService refreshService = new RefreshService(
				userRepository,
				accessTokenIssuer,
				() -> "new-refresh",
				token -> "hash:" + token,
				new SingleRefreshTokenStore(user.id(), user.sessionVersion()),
				Duration.ofMinutes(15),
				Duration.ofDays(14),
				CLOCK);
			AuthController authController = new AuthController(
				null, null, null, null, refreshService, null, null,
				new AuthCookieWriter(false, "Lax", Duration.ofMinutes(15), Duration.ofDays(14)));
			AuthenticationRequestProtectionFilter protection = new AuthenticationRequestProtectionFilter(
				new OneRequestRedisTemplate(), 20, Duration.ofMinutes(1), ORIGIN);
			AuthenticationFilter authentication = new AuthenticationFilter(accessTokenIssuer, userRepository);
			mvc = MockMvcBuilders.standaloneSetup(new AccountWithdrawalController(withdrawalService), authController)
				.setControllerAdvice(new ApiExceptionHandler())
				.addFilters(new TraceIdFilter(), protection, authentication)
				.build();
		}

		static Fixture active() {
			return new Fixture(userCreatedAt(NOW.minusSeconds(1)));
		}

		static Fixture pending(Instant dueAt) {
			Instant requestedAt = dueAt.minus(Duration.ofDays(7));
			return new Fixture(userCreatedAt(requestedAt.minusSeconds(1)).requestWithdrawal(requestedAt, dueAt, 3));
		}

		MockMvc mvc() {
			return mvc;
		}
	}

	private static User userCreatedAt(Instant createdAt) {
		return User.createAdmin(
			new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
			new EncryptedEmail("v1:test:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA=="),
			new EmailSearchHash("0".repeat(64)),
			new PasswordHash("password-hash"),
			createdAt);
	}

	private static final class InMemoryUserRepository implements UserRepository {

		private final AtomicReference<User> user;

		private InMemoryUserRepository(User user) {
			this.user = new AtomicReference<>(user);
		}

		@Override
		public User save(User next) {
			user.set(next);
			return next;
		}

		@Override
		public Optional<User> findById(UserId userId) {
			return user.get().id().equals(userId) ? Optional.of(user.get()) : Optional.empty();
		}

		@Override
		public Optional<User> findByEmailSearchHash(EmailSearchHash emailSearchHash) {
			return Optional.empty();
		}

		@Override
		public boolean existsByEmailSearchHash(EmailSearchHash emailSearchHash) {
			return false;
		}

		@Override
		public boolean existsByRole(UserRole role) {
			return false;
		}
	}

	private static final class InMemoryAccessTokenIssuer implements AccessTokenIssuer {

		private final Map<String, AccessTokenClaims> claimsByToken = new HashMap<>();

		void allow(String token, User user) {
			claimsByToken.put(token, new AccessTokenClaims(
				user.id(), user.role(), user.status(), user.emailVerificationStatus(), user.sessionVersion(), NOW.plus(Duration.ofMinutes(15))));
		}

		@Override
		public String issue(User user, Duration ttl) {
			String token = "refreshed-access";
			allow(token, user);
			return token;
		}

		@Override
		public AccessTokenClaims verify(String token) {
			AccessTokenClaims claims = claimsByToken.get(token);
			if (claims == null) {
				throw new IllegalArgumentException("만료되었거나 알 수 없는 Access Token입니다.");
			}
			return claims;
		}
	}

	private static final class SingleRefreshTokenStore implements RefreshTokenStore {

		private final UserId userId;
		private final long sessionVersion;
		private String activeHash = "hash:pending-refresh";

		private SingleRefreshTokenStore(UserId userId, long sessionVersion) {
			this.userId = userId;
			this.sessionVersion = sessionVersion;
		}

		@Override
		public void store(RefreshTokenCommand command) {
			activeHash = command.tokenHash();
		}

		@Override
		public RefreshTokenRotationResult rotate(RotateRefreshTokenCommand command) {
			if (!command.oldTokenHash().equals(activeHash)) {
				return RefreshTokenRotationResult.missing();
			}
			activeHash = command.newTokenHash();
			return RefreshTokenRotationResult.rotated(userId, sessionVersion);
		}

		@Override
		public void revoke(String tokenHash) {
			if (tokenHash.equals(activeHash)) {
				activeHash = "revoked";
			}
		}

		@Override
		public void revokeAll(UserId userId, Duration ttl) {
			if (this.userId.equals(userId)) {
				activeHash = "revoked";
			}
		}
	}

	private static final class OneRequestRedisTemplate extends StringRedisTemplate {

		@Override
		@SuppressWarnings("unchecked")
		public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
			return (T) Long.valueOf(1);
		}
	}
}
