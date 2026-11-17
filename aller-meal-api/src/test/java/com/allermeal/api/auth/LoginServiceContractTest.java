package com.allermeal.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.allermeal.api.auth.response.LoginResponse;
import com.allermeal.application.auth.*;
import com.allermeal.application.port.out.*;
import com.allermeal.application.port.out.result.RefreshTokenLoginSnapshotResult;
import com.allermeal.domain.common.EntityTimestamps;
import com.allermeal.domain.user.*;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import io.swagger.v3.core.converter.ModelConverters;

final class LoginServiceContractTest {

	private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
	private static final Instant REQUESTED = NOW.minus(Duration.ofDays(7));
	private final UserRepository users = mock(UserRepository.class);
	private final PasswordHasher passwords = mock(PasswordHasher.class);
	private final AccessTokenIssuer issuer = mock(AccessTokenIssuer.class);
	private final RefreshTokenStore tokens = mock(RefreshTokenStore.class);
	private final LoginAttemptStore attempts = mock(LoginAttemptStore.class);
	private final LoginService service = new LoginService(users, email -> new EmailSearchHash("0".repeat(64)),
		passwords, issuer, () -> "generated-token", value -> "hash:" + value, tokens, attempts,
		Duration.ofMinutes(15), Duration.ofDays(14), Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void activeLoginPreservesResponseFieldsAndAddsUserStatus() {
		User user = user(UserStatus.ACTIVE, EmailVerificationStatus.VERIFIED, null);
		prepare(user);
		AuthenticationResult result = service.login(command("correct-password"));
		var response = JsonMapper.builder().build().valueToTree(LoginResponse.from(result));
		assertEquals("ACTIVE", response.path("userStatus").asText());
		assertEquals(user.id().value().toString(), response.path("userId").asText());
		assertEquals("VERIFIED", response.path("emailVerificationStatus").asText());
		assertEquals(NOW.plusSeconds(900), result.accessTokenExpiresAt());
		assertEquals(NOW.plus(Duration.ofDays(14)), result.refreshTokenExpiresAt());
		assertFalse(response.path("sessionId").asText().isBlank());
		assertTrue(response.has("accessTokenExpiresAt"));
		assertTrue(response.has("refreshTokenExpiresAt"));
		assertFalse(response.has("accessToken"));
		assertFalse(response.has("refreshToken"));
		verify(tokens).store(argThat(stored -> stored.userId().equals(user.id())
			&& stored.sessionVersion() == user.sessionVersion()));
	}

	@Test
	void pendingLoginSucceedsAtLastNanosecondBeforeDeadlineAndReportsStatus() {
		User user = user(UserStatus.WITHDRAWAL_PENDING, EmailVerificationStatus.VERIFIED, NOW.plusNanos(1));
		prepare(user);
		AuthenticationResult result = service.login(command("correct-password"));
		var response = JsonMapper.builder().build().valueToTree(LoginResponse.from(result));
		assertEquals("WITHDRAWAL_PENDING", response.path("userStatus").asText());
		verify(issuer).issueUntil(eq(user), eq(NOW.plusSeconds(900)), anyString());
		verify(tokens).store(any());
	}

	@ParameterizedTest
	@ValueSource(longs = {0, -1})
	void pendingLoginRejectsExactDeadlineAndAfterDeadline(long deadlineOffsetNanos) {
		prepare(user(UserStatus.WITHDRAWAL_PENDING, EmailVerificationStatus.VERIFIED, NOW.plusNanos(deadlineOffsetNanos)));
		assertThrows(InvalidLoginCredentialsException.class, () -> service.login(command("correct-password")));
		verifyNoInteractions(issuer);
		verify(tokens, never()).store(any());
	}

	@ParameterizedTest
	@EnumSource(value = UserStatus.class, names = {"SUSPENDED", "DISABLED"})
	void restrictedAccountsCannotLogin(UserStatus status) {
		prepare(user(status, EmailVerificationStatus.VERIFIED, null));
		Class<? extends RuntimeException> expected = status == UserStatus.SUSPENDED
			? AccountSuspendedException.class : InvalidLoginCredentialsException.class;
		assertThrows(expected,
			() -> service.login(command("correct-password")));
		verifyNoInteractions(issuer);
		verify(tokens, never()).store(any());
	}

	@Test
	void pendingAccountStillRequiresCorrectPassword() {
		prepare(user(UserStatus.WITHDRAWAL_PENDING, EmailVerificationStatus.VERIFIED, NOW.plusSeconds(60)));
		assertThrows(InvalidLoginCredentialsException.class, () -> service.login(command("wrong-password")));
		verify(attempts).recordFailure("0".repeat(64));
		verifyNoInteractions(issuer);
		verify(tokens, never()).store(any());
	}

	@Test
	void pendingAccountStillRequiresVerifiedEmail() {
		prepare(user(UserStatus.WITHDRAWAL_PENDING, EmailVerificationStatus.UNVERIFIED, NOW.plusSeconds(60)));
		assertThrows(EmailNotVerifiedException.class, () -> service.login(command("correct-password")));
		verifyNoInteractions(issuer);
		verify(tokens, never()).store(any());
	}

	@Test
	void lockedPendingAccountDoesNotValidatePasswordOrIssueTokens() {
		when(attempts.isLocked(anyString())).thenReturn(true);
		assertThrows(LoginTemporarilyLockedException.class, () -> service.login(command("correct-password")));
		verifyNoInteractions(users, passwords, issuer, tokens);
	}

	@Test
	void lockEstablishedDuringPasswordVerificationStillBlocksPendingLogin() {
		prepare(user(UserStatus.WITHDRAWAL_PENDING, EmailVerificationStatus.VERIFIED, NOW.plusSeconds(60)));
		when(attempts.clearIfUnlocked(anyString())).thenReturn(false);
		assertThrows(LoginTemporarilyLockedException.class, () -> service.login(command("correct-password")));
		verifyNoInteractions(issuer);
		verify(tokens, never()).store(any());
	}

	@Test
	void invalidPendingPersistenceDeadlinesAreRejectedBeforeLogin() {
		assertThrows(IllegalArgumentException.class,
			() -> user(UserStatus.WITHDRAWAL_PENDING, EmailVerificationStatus.VERIFIED, null));
		assertThrows(IllegalArgumentException.class,
			() -> user(UserStatus.WITHDRAWAL_PENDING, EmailVerificationStatus.VERIFIED, REQUESTED));
	}

	@Test
	void openApiLoginResponseIncludesExistingStatusEnumAndPreservesExistingFields() {
		io.swagger.v3.oas.models.media.Schema<?> schema = ModelConverters.getInstance().read(LoginResponse.class).get("LoginResponse");
		assertNotNull(schema);
		assertTrue(schema.getProperties().keySet().containsAll(java.util.Set.of("userId", "emailVerificationStatus",
			"accessTokenExpiresAt", "refreshTokenExpiresAt", "sessionId", "userStatus")));
		assertEquals(java.util.List.of("ACTIVE", "WITHDRAWAL_PENDING", "SUSPENDED", "DISABLED"),
			schema.getProperties().get("userStatus").getEnum());
	}

	private void prepare(User user) {
		when(users.findByEmailSearchHash(any())).thenReturn(Optional.of(user));
		when(passwords.matches("correct-password", "password-hash")).thenReturn(true);
		when(attempts.clearIfUnlocked(anyString())).thenReturn(true);
		when(tokens.loginSnapshot(user.id())).thenReturn(new RefreshTokenLoginSnapshotResult(NOW, "generation", null));
		when(issuer.issueUntil(eq(user), any(), anyString())).thenReturn("access-token");
	}

	private static LoginCommand command(String password) {
		return new LoginCommand("owner@example.test", password, null);
	}

	private static User user(UserStatus status, EmailVerificationStatus verification, Instant dueAt) {
		return User.restoreFromPersistence(new UserId(UUID.randomUUID()),
			new EncryptedEmail("v1:test:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA=="),
			new EmailSearchHash("0".repeat(64)), new PasswordHash("password-hash"), UserRole.MEMBER,
			status, verification, status == UserStatus.WITHDRAWAL_PENDING ? REQUESTED : null, dueAt,
			null, 3, EntityTimestamps.createdAt(REQUESTED.minusSeconds(1)), 0);
	}
}
