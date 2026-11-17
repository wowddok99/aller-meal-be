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
import com.allermeal.application.auth.LoginService;
import com.allermeal.application.port.out.LoginAttemptStore;
import com.allermeal.application.port.out.AccessTokenIssuer;
import com.allermeal.application.port.out.AccountWithdrawalPrivacyRepository;
import com.allermeal.application.port.out.EmailVerificationTokenHasher;
import com.allermeal.application.port.out.RefreshTokenStore;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.VerificationTokenGenerator;
import com.allermeal.application.port.out.command.RefreshTokenCommand;
import com.allermeal.application.port.out.command.RotateRefreshTokenCommand;
import com.allermeal.application.port.out.result.RefreshTokenRotationResult;
import com.allermeal.application.port.out.result.RefreshTokenLoginSnapshotResult;
import com.allermeal.application.port.out.result.RefreshSessionLookupResult;
import com.allermeal.application.port.out.result.RefreshOperationReservationResult;
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
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

final class AccountWithdrawalHttpContractTest {

	private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
	private static final String ORIGIN = "http://localhost:3000";
	private static final String CSRF = "csrf-token";

	@Test
	void freshPendingLoginProfilePreservesSessionAndRestrictionsForWithdrawalCancellation() throws Exception {
		Fixture fixture = Fixture.pending(NOW.plusNanos(1));
		MvcResult login = fixture.mvc().perform(post("/api/v1/auth/login")
			.header(HttpHeaders.ORIGIN, ORIGIN).contentType("application/json")
			.content("{\"email\":\"owner@example.test\",\"password\":\"correct-password\"}"))
			.andExpect(status().isOk()).andReturn();
		assertEquals("WITHDRAWAL_PENDING", jsonStringField(login.getResponse().getContentAsString(), "userStatus"));
		String access = cookieValue(login, AuthCookieWriter.ACCESS_TOKEN_COOKIE);
		String refresh = cookieValue(login, AuthCookieWriter.REFRESH_TOKEN_COOKIE);
		String csrf = cookieValue(login, AuthCookieWriter.CSRF_TOKEN_COOKIE);
		String session = jsonStringField(login.getResponse().getContentAsString(), "sessionId");
		for (boolean boundSession : List.of(false, true)) {
			var request = get("/api/v1/account/profile").cookie(accessCookie(access), refreshCookie(refresh));
			if (boundSession) request.header("X-Expected-Session-Id", session);
			MvcResult profile = fixture.mvc().perform(request).andExpect(status().isOk()).andReturn();
			assertEquals("WITHDRAWAL_PENDING", jsonStringField(profile.getResponse().getContentAsString(), "userStatus"));
			assertEquals("ADMIN", jsonStringField(profile.getResponse().getContentAsString(), "role"));
			assertTrue(profile.getResponse().getHeaders(HttpHeaders.SET_COOKIE).isEmpty());
			fixture.mvc().perform(get("/api/v1/protected-probe").cookie(accessCookie(access)))
				.andExpect(status().isForbidden());
		}
		fixture.mvc().perform(post("/api/v1/auth/session-status")
			.cookie(refreshCookie(refresh), csrfCookie(csrf)).header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", csrf)).andExpect(status().isOk());
		fixture.mvc().perform(get("/api/v1/account/withdrawal")
			.cookie(accessCookie(access), refreshCookie(refresh)).header("X-Expected-Session-Id", session))
			.andExpect(status().isOk());
		fixture.mvc().perform(delete("/api/v1/account/withdrawal")
			.cookie(accessCookie(access), refreshCookie(refresh), csrfCookie(csrf))
			.header("X-Expected-Session-Id", session).header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", csrf)).andExpect(status().isNoContent());
		fixture.mvc().perform(get("/api/v1/account/profile")
			.cookie(accessCookie(access), refreshCookie(refresh)).header("X-Expected-Session-Id", session))
			.andExpect(status().isOk());
	}

	@Test
	void pendingReloginCanBindRefreshAndCancelThenUseOrdinaryApi() throws Exception {
		Fixture fixture = Fixture.pending(NOW.plusSeconds(60));
		MvcResult login = fixture.mvc().perform(post("/api/v1/auth/login")
			.header(HttpHeaders.ORIGIN, ORIGIN).contentType("application/json")
			.content("{\"email\":\"owner@example.test\",\"password\":\"correct-password\"}"))
			.andExpect(status().isOk()).andReturn();
		assertEquals("WITHDRAWAL_PENDING", jsonStringField(login.getResponse().getContentAsString(), "userStatus"));
		String access = cookieValue(login, AuthCookieWriter.ACCESS_TOKEN_COOKIE);
		String refresh = cookieValue(login, AuthCookieWriter.REFRESH_TOKEN_COOKIE);
		String csrf = cookieValue(login, AuthCookieWriter.CSRF_TOKEN_COOKIE);
		String session = jsonStringField(login.getResponse().getContentAsString(), "sessionId");
		fixture.mvc().perform(get("/api/v1/protected-probe").cookie(accessCookie(access)))
			.andExpect(status().isForbidden());
		fixture.mvc().perform(post("/api/v1/auth/session-status")
			.cookie(refreshCookie(refresh), csrfCookie(csrf)).header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", csrf)).andExpect(status().isOk());
		fixture.mvc().perform(get("/api/v1/account/withdrawal")
			.cookie(accessCookie(access), refreshCookie(refresh)).header("X-Expected-Session-Id", session))
			.andExpect(status().isOk());
		fixture.mvc().perform(get("/api/v1/account/withdrawal")
			.cookie(accessCookie(access), refreshCookie(refresh)).header("X-Expected-Session-Id", "wrong-session"))
			.andExpect(status().isUnauthorized());
		MvcResult reservation = fixture.mvc().perform(post("/api/v1/auth/refresh-operations")
			.cookie(refreshCookie(refresh), csrfCookie(csrf)).header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", csrf)).andExpect(status().isCreated()).andReturn();
		String operation = jsonStringField(reservation.getResponse().getContentAsString(), "operationId");
		fixture.mvc().perform(post("/api/v1/auth/refresh-operations/" + operation + "/cancel")
			.cookie(refreshCookie(refresh), csrfCookie(csrf)).header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", csrf)).andExpect(status().isOk());
		MvcResult nextReservation = fixture.mvc().perform(post("/api/v1/auth/refresh-operations")
			.cookie(refreshCookie(refresh), csrfCookie(csrf)).header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", csrf)).andExpect(status().isCreated()).andReturn();
		operation = jsonStringField(nextReservation.getResponse().getContentAsString(), "operationId");
		MvcResult refreshed = fixture.mvc().perform(post("/api/v1/auth/refresh-operations/" + operation + "/execute")
			.cookie(refreshCookie(refresh), csrfCookie(csrf)).header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", csrf)).andExpect(status().isOk()).andReturn();
		access = cookieValue(refreshed, AuthCookieWriter.ACCESS_TOKEN_COOKIE);
		refresh = cookieValue(refreshed, AuthCookieWriter.REFRESH_TOKEN_COOKIE);
		csrf = cookieValue(refreshed, AuthCookieWriter.CSRF_TOKEN_COOKIE);
		fixture.mvc().perform(delete("/api/v1/account/withdrawal")
			.cookie(accessCookie(access), refreshCookie(refresh), csrfCookie(csrf))
			.header("X-Expected-Session-Id", session).header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", csrf)).andExpect(status().isNoContent());
		fixture.mvc().perform(get("/api/v1/protected-probe")
			.cookie(accessCookie(access), refreshCookie(refresh)).header("X-Expected-Session-Id", session))
			.andExpect(status().isOk());
	}

	@Test
	void pendingLogoutClearsCookiesAndRevokesBoundSession() throws Exception {
		Fixture fixture = Fixture.pending(NOW.plusSeconds(60));
		MvcResult logout = fixture.mvc().perform(post("/api/v1/auth/logout")
			.cookie(refreshCookie("pending-refresh"), csrfCookie(CSRF))
			.header("X-Expected-Session-Id", "hash:test-family").header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", CSRF)).andExpect(status().isNoContent()).andReturn();
		assertEquals(3, logout.getResponse().getHeaders(HttpHeaders.SET_COOKIE).size());
		fixture.mvc().perform(post("/api/v1/auth/session-status")
			.cookie(refreshCookie("pending-refresh"), csrfCookie(CSRF)).header(HttpHeaders.ORIGIN, ORIGIN)
			.header("X-CSRF-Token", CSRF)).andExpect(status().isUnauthorized());
	}

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

	@Test
	void exactDeadlineAndLaterCannotCancelOrRestoreOrdinaryAccess() throws Exception {
		for (Instant dueAt : List.of(NOW, NOW.minusNanos(1))) {
			Fixture fixture = Fixture.pending(dueAt);
			fixture.mvc().perform(delete("/api/v1/account/withdrawal")
				.cookie(accessCookie("valid-access"), csrfCookie(CSRF)).header(HttpHeaders.ORIGIN, ORIGIN)
				.header("X-CSRF-Token", CSRF)).andExpect(status().isConflict());
			fixture.mvc().perform(get("/api/v1/protected-probe").cookie(accessCookie("valid-access")))
				.andExpect(status().isUnauthorized());
			fixture.mvc().perform(post("/api/v1/auth/session-status")
				.cookie(refreshCookie("pending-refresh"), csrfCookie(CSRF)).header(HttpHeaders.ORIGIN, ORIGIN)
				.header("X-CSRF-Token", CSRF)).andExpect(status().isUnauthorized());
		}
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
			SingleRefreshTokenStore tokenStore = new SingleRefreshTokenStore(user.id(), user.sessionVersion());
			AtomicInteger operationSequence = new AtomicInteger();
			RefreshService refreshService = new RefreshService(
				userRepository,
				accessTokenIssuer,
				() -> "refresh_operation_identifier_" + operationSequence.incrementAndGet(),
				token -> "hash:" + token,
				tokenStore,
				Duration.ofMinutes(15),
				Duration.ofDays(14),
				CLOCK);
			LoginService loginService = new LoginService(userRepository, email -> user.emailSearchHash(),
				new com.allermeal.application.port.out.PasswordHasher() {
					public String hash(String password) { return "password-hash"; }
					public boolean matches(String password, String hash) { return password.equals("correct-password"); }
				}, accessTokenIssuer, () -> "new-refresh", token -> "hash:" + token, tokenStore,
				new LoginAttemptStore() {
					public boolean isLocked(String hash) { return false; }
					public boolean recordFailure(String hash) { return false; }
					public void clear(String hash) { }
					public boolean clearIfUnlocked(String hash) { return true; }
				}, Duration.ofMinutes(15), Duration.ofDays(14), CLOCK);
			AuthController authController = new AuthController(
				null, null, null, loginService, refreshService, null, null,
				new AuthCookieWriter(false, "Lax", Duration.ofMinutes(15), Duration.ofDays(14)));
			AuthenticationRequestProtectionFilter protection = new AuthenticationRequestProtectionFilter(
				new OneRequestRedisTemplate(), 20, Duration.ofMinutes(1), ORIGIN);
			AuthenticationFilter authentication = new AuthenticationFilter(accessTokenIssuer, userRepository, refreshService, CLOCK);
			mvc = MockMvcBuilders.standaloneSetup(new AccountWithdrawalController(withdrawalService), authController, new ProtectedProbe(),
				new AccountProfileController(new com.allermeal.application.account.AccountProfileService(
					id -> userRepository.findById(id).map(current -> new com.allermeal.application.port.out.result.AccountProfileDataResult(
						current.displayName(), current.encryptedEmail().value(), current.status(), current.emailVerificationStatus(),
						current.sessionVersion(), current.personalDataDeletedAt() != null, current.withdrawalDueAt(), current.role())),
					cipher -> "owner@example.test", CLOCK)))
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
			return user.get().emailSearchHash().equals(emailSearchHash) ? Optional.of(user.get()) : Optional.empty();
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
		public String issueUntil(User user, Instant expiresAt, String sessionId) {
			String token = "issued-access";
			claimsByToken.put(token, new AccessTokenClaims(user.id(), user.role(), user.status(),
				user.emailVerificationStatus(), user.sessionVersion(), expiresAt, sessionId));
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
		private String family = "test-family";
		private String reservedOperation;

		private SingleRefreshTokenStore(UserId userId, long sessionVersion) {
			this.userId = userId;
			this.sessionVersion = sessionVersion;
		}

		@Override
		public void store(RefreshTokenCommand command) {
			activeHash = command.tokenHash();
			family = command.familyId();
		}

		@Override
		public RefreshTokenLoginSnapshotResult loginSnapshot(UserId id) {
			return new RefreshTokenLoginSnapshotResult(NOW, "generation", null);
		}

		@Override
		public RefreshSessionLookupResult lookup(String hash) {
			return activeHash.equals(hash) ? new RefreshSessionLookupResult(userId, sessionVersion, family) : null;
		}

		@Override
		public RefreshOperationReservationResult reserve(String hash, String operation) {
			if (!activeHash.equals(hash)) return new RefreshOperationReservationResult("UNAUTHORIZED", null, null);
			if (reservedOperation != null) return new RefreshOperationReservationResult("BUSY", null, null);
			reservedOperation = operation;
			return new RefreshOperationReservationResult("RESERVED", family, NOW.plusSeconds(30));
		}

		@Override
		public String cancel(String hash, String operation) {
			if (!activeHash.equals(hash)) return "UNAUTHORIZED";
			if (!operation.equals(reservedOperation)) return "EXPIRED";
			reservedOperation = null;
			return "CANCELLED";
		}

		@Override
		public String findLogoutFamily(String hash) { return activeHash.equals(hash) ? family : null; }

		@Override
		public boolean revokeIfFamily(String hash, String expected) {
			if (!activeHash.equals(hash) || !family.equals(expected)) return false;
			revoke(hash);
			return true;
		}

		@Override
		public RefreshTokenRotationResult rotate(RotateRefreshTokenCommand command) {
			if (!command.oldTokenHash().equals(activeHash)) {
				return RefreshTokenRotationResult.missing();
			}
			if (command.operationId() != null && !command.operationId().equals(reservedOperation)) {
				return RefreshTokenRotationResult.operationExpired();
			}
			reservedOperation = null;
			activeHash = command.newTokenHash();
			return RefreshTokenRotationResult.rotated(userId, sessionVersion, null, null, null, family);
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

	@RestController
	private static final class ProtectedProbe {
		@GetMapping("/api/v1/protected-probe")
		public Map<String, String> get() { return Map.of("result", "ok"); }
	}
}
