package com.allermeal.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.allermeal.application.auth.*;
import com.allermeal.application.port.out.*;
import com.allermeal.application.port.out.result.*;
import com.allermeal.domain.user.*;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class RefreshServiceContractTest {
	private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
	private static final String OPERATION = "operation_identifier_12345";
	private final User user = User.createAdmin(new UserId(UUID.randomUUID()),
		new EncryptedEmail("v1:test:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA=="),
		new EmailSearchHash("0".repeat(64)), new PasswordHash("password-hash"), NOW);
	private final UserRepository users = mock(UserRepository.class);
	private final AccessTokenIssuer issuer = mock(AccessTokenIssuer.class);
	private final RefreshTokenStore store = mock(RefreshTokenStore.class);
	private final RefreshService service = new RefreshService(users, issuer, () -> OPERATION,
		value -> "hash:" + value, store, Duration.ofMinutes(15), Duration.ofDays(14), Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void reservationUsesActiveFamilyAndRejectsBusyOrChangedFamily() {
		when(store.lookup("hash:refresh")).thenReturn(new RefreshSessionLookupResult(user.id(), user.sessionVersion(), "family"));
		when(users.findById(user.id())).thenReturn(Optional.of(user));
		when(store.reserve("hash:refresh", OPERATION)).thenReturn(
			new RefreshOperationReservationResult("RESERVED", "family", NOW.plusSeconds(30)),
			new RefreshOperationReservationResult("BUSY", null, null),
			new RefreshOperationReservationResult("RESERVED", "other-family", NOW.plusSeconds(30)));
		RefreshOperationResult reserved = service.reserve("refresh");
		assertEquals(OPERATION, reserved.operationId());
		assertEquals("hash:family", reserved.sessionId());
		assertThrows(RefreshInProgressException.class, () -> service.reserve("refresh"));
		assertThrows(UnauthorizedAccessException.class, () -> service.reserve("refresh"));
	}

	@Test
	void executeRotatesAndReplayOrExpiredOperationDoesNotIssueTokens() {
		when(store.rotate(any())).thenReturn(
			RefreshTokenRotationResult.rotated(user.id(), user.sessionVersion(), true, NOW.plusSeconds(60), NOW.plusSeconds(60), "family"),
			RefreshTokenRotationResult.reused(), RefreshTokenRotationResult.operationExpired());
		when(users.findById(user.id())).thenReturn(Optional.of(user));
		when(issuer.issueUntil(user, NOW.plusSeconds(60), "hash:family")).thenReturn("new-access");
		AuthenticationResult result = service.execute("refresh", OPERATION);
		assertEquals("hash:family", result.sessionId());
		assertEquals(NOW.plusSeconds(60), result.accessTokenExpiresAt());
		assertThrows(UnauthorizedAccessException.class, () -> service.execute("refresh", OPERATION));
		assertThrows(RefreshOperationExpiredException.class, () -> service.execute("refresh", OPERATION));
		verify(issuer, times(1)).issueUntil(any(), any(), any());
		verify(store, times(3)).rotate(argThat(command -> OPERATION.equals(command.operationId())));
	}

	@Test
	void cancellationNeverIssuesTokensAndRequiresSameFamilyAuthorization() {
		when(store.cancel("hash:refresh", OPERATION)).thenReturn("CANCELLED", "ROTATED", "EXPIRED", "UNAUTHORIZED");
		assertEquals("CANCELLED", service.cancel("refresh", OPERATION));
		assertEquals("ROTATED", service.cancel("refresh", OPERATION));
		assertEquals("EXPIRED", service.cancel("refresh", OPERATION));
		assertThrows(UnauthorizedAccessException.class, () -> service.cancel("refresh", OPERATION));
		assertThrows(UnauthorizedAccessException.class, () -> service.cancel("refresh", "short"));
		verifyNoInteractions(issuer);
	}

	@Test
	void staleSessionVersionCannotReserveAndRotationIsRevoked() {
		when(users.findById(user.id())).thenReturn(Optional.of(user));
		when(store.lookup("hash:refresh")).thenReturn(new RefreshSessionLookupResult(user.id(), user.sessionVersion() + 1, "family"));
		assertThrows(UnauthorizedAccessException.class, () -> service.sessionStatus("refresh"));
		when(store.rotate(any())).thenReturn(RefreshTokenRotationResult.rotated(user.id(), user.sessionVersion() + 1,
			true, NOW.plusSeconds(60), NOW.plusSeconds(60), "family"));
		assertThrows(UnauthorizedAccessException.class, () -> service.execute("refresh", OPERATION));
		verify(store).revoke("hash:" + OPERATION);
		verifyNoInteractions(issuer);
	}
}
