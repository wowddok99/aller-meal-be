package com.allermeal.api.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.api.error.ApiExceptionHandler;
import com.allermeal.application.account.AccountProfileService;
import com.allermeal.application.auth.AccessTokenClaims;
import com.allermeal.application.auth.RefreshService;
import com.allermeal.application.port.out.*;
import com.allermeal.application.port.out.result.AccountProfileDataResult;
import com.allermeal.domain.user.*;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountProfileHttpContractTest {
	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final String CIPHER = "v1:test:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA==";
	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
	private final User owner = user().withProfile("이름", "STUDENT");
	private final User other = user().withProfile("다른 이름", "STUDENT");
	private final UserRepository users = mock(UserRepository.class);
	private final AccessTokenIssuer tokens = mock(AccessTokenIssuer.class);
	private final RefreshService refresh = mock(RefreshService.class);
	private final AccountProfileRepository profiles = mock(AccountProfileRepository.class);
	private final EmailDecryptor decryptor = mock(EmailDecryptor.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AccountProfileController(new AccountProfileService(profiles, decryptor, clock)))
		.setControllerAdvice(new ApiExceptionHandler()).addFilters(new AuthenticationFilter(tokens, users, refresh, java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC))).build();

	private static User user() {
		return User.create(new UserId(UUID.randomUUID()), new EncryptedEmail(CIPHER),
			new EmailSearchHash("a".repeat(64)), new PasswordHash("password"), NOW).verifyEmail(NOW.plusSeconds(1));
	}
	private void authenticate(User user) {
		when(tokens.verify("token")).thenReturn(new AccessTokenClaims(user.id(), user.role(), user.status(),
			user.emailVerificationStatus(), user.sessionVersion(), NOW.plusSeconds(60), "session"));
		when(users.findById(user.id())).thenReturn(Optional.of(user));
	}
	private AccountProfileDataResult data(User user, String name) {
		return new AccountProfileDataResult(name, CIPHER, user.status(), user.emailVerificationStatus(), user.sessionVersion(), false, user.withdrawalDueAt(), user.role());
	}
	@Test void returnsOnlyOwnerAndNeverAcceptsAnotherUserIdentifier() throws Exception {
		authenticate(owner);
		when(profiles.findForUpdate(owner.id())).thenReturn(Optional.of(data(owner, "이름")));
		when(decryptor.decrypt(any())).thenReturn("owner@example.test");
		mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token").param("userId", other.id().value().toString()))
			.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(content().json("{\"displayName\":\"이름\",\"email\":\"owner@example.test\",\"userStatus\":\"ACTIVE\",\"role\":\"MEMBER\"}", true));
		verify(profiles).findForUpdate(owner.id());
		verify(profiles, never()).findForUpdate(other.id());
	}
	@Test void legacyNameCanBeNull() throws Exception {
		authenticate(owner);
		when(profiles.findForUpdate(owner.id())).thenReturn(Optional.of(data(owner, null)));
		when(decryptor.decrypt(any())).thenReturn("owner@example.test");
		mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token"))
			.andExpect(status().isOk()).andExpect(content().json("{\"displayName\":null,\"email\":\"owner@example.test\",\"userStatus\":\"ACTIVE\",\"role\":\"MEMBER\"}", true));
	}
	@Test void missingAuthenticationAndOtherSessionAreRejected() throws Exception {
		authenticate(owner);
		mvc.perform(get("/api/v1/account/profile")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token")
			.header("X-Expected-Session-Id", "other-session")).andExpect(status().isUnauthorized());
		verifyNoInteractions(profiles, decryptor);
	}
	@Test void filterBlocksSuspendedDisabledAndUnverified() throws Exception {
		User unverified = User.create(new UserId(UUID.randomUUID()), new EncryptedEmail(CIPHER),
			new EmailSearchHash("b".repeat(64)), new PasswordHash("password"), NOW);
		for (User user : new User[] { owner.suspend(NOW.plusSeconds(1)), owner.disable(NOW.plusSeconds(1)), unverified }) {
			authenticate(user);
			int expected = user.status() == UserStatus.WITHDRAWAL_PENDING || user.status() == UserStatus.SUSPENDED || user.emailVerificationStatus() != EmailVerificationStatus.VERIFIED ? 403 : 401;
			mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token")).andExpect(status().is(expected));
		}
		verifyNoInteractions(profiles, decryptor);
	}
	@Test void pendingCanReadOnlyOwnProfileAndNoOtherPathOrMethod() throws Exception {
		User pending = owner.requestWithdrawal(NOW.plusSeconds(1), NOW.plusSeconds(600));
		authenticate(pending);
		when(profiles.findForUpdate(owner.id())).thenReturn(Optional.of(data(pending, "이름")));
		when(decryptor.decrypt(any())).thenReturn("owner@example.test");
		mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token"))
			.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.userStatus").value("WITHDRAWAL_PENDING"))
			.andExpect(jsonPath("$.role").value("MEMBER"));
		mvc.perform(get("/api/v1/children").header("Authorization", "Bearer token"))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_WITHDRAWAL_PENDING"));
		mvc.perform(get("/api/v1/account/profile/other").header("Authorization", "Bearer token")).andExpect(status().isForbidden());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/account/profile")
			.header("Authorization", "Bearer token")).andExpect(status().isForbidden());
		verify(profiles, times(1)).findForUpdate(owner.id());
	}

	@Test void authoritativeStateAndRoleComeFromLockedRowInsteadOfTokenOrFilter() throws Exception {
		User admin = owner.promoteToAdmin(NOW.plusSeconds(1));
		authenticate(admin);
		when(profiles.findForUpdate(owner.id())).thenReturn(Optional.of(data(owner, "최신 이름")));
		when(decryptor.decrypt(any())).thenReturn("owner@example.test");
		mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MEMBER"));
		authenticate(owner);
		when(profiles.findForUpdate(owner.id())).thenReturn(Optional.of(data(admin.requestWithdrawal(NOW.plusSeconds(2), NOW.plusSeconds(600)), "최신 이름")));
		mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.userStatus").value("WITHDRAWAL_PENDING"));
		User pending = owner.requestWithdrawal(NOW.plusSeconds(1), NOW.plusSeconds(600));
		authenticate(pending);
		when(profiles.findForUpdate(owner.id())).thenReturn(Optional.of(data(owner, "최신 이름")));
		mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.userStatus").value("ACTIVE"));
	}
	@Test void pendingAdminStillCannotAccessGeneralOrAdminApis() throws Exception {
		User pending = owner.promoteToAdmin(NOW.plusSeconds(1)).requestWithdrawal(NOW.plusSeconds(2), NOW.plusSeconds(600));
		authenticate(pending);
		for (String path : new String[] {"/api/v1/children", "/api/v1/admin/users"}) {
			mvc.perform(get(path).header("Authorization", "Bearer token")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ACCOUNT_WITHDRAWAL_PENDING"));
		}
		verifyNoInteractions(profiles, decryptor);
	}
	@Test void lockedPendingNullExactAndPastDeadlineCannotDecrypt() throws Exception {
		authenticate(owner);
		for (Instant due : new Instant[] {null, NOW, NOW.minusNanos(1)}) {
			when(profiles.findForUpdate(owner.id())).thenReturn(Optional.of(new AccountProfileDataResult(
				"이름", CIPHER, UserStatus.WITHDRAWAL_PENDING, EmailVerificationStatus.VERIFIED,
				owner.sessionVersion(), false, due, UserRole.MEMBER)));
			mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token")).andExpect(status().isUnauthorized());
		}
		verifyNoInteractions(decryptor);
	}
	@Test void clockIsReadAfterLockedRepositoryReturns() throws Exception {
		Clock movingClock = mock(Clock.class);
		when(movingClock.instant()).thenReturn(NOW, NOW.plusSeconds(600));
		User pending = owner.requestWithdrawal(NOW.plusSeconds(1), NOW.plusSeconds(600));
		authenticate(pending);
		when(profiles.findForUpdate(owner.id())).thenReturn(Optional.of(data(pending, "이름")));
		MockMvc raceMvc = MockMvcBuilders.standaloneSetup(new AccountProfileController(new AccountProfileService(profiles, decryptor, movingClock)))
			.setControllerAdvice(new ApiExceptionHandler()).addFilters(new AuthenticationFilter(tokens, users, refresh, movingClock)).build();
		raceMvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token")).andExpect(status().isUnauthorized());
		verify(profiles).findForUpdate(owner.id());
		verifyNoInteractions(decryptor);
	}
	@Test void expiredPendingIsRejectedByFilterBeforeRepository() throws Exception {
		for (Instant due : new Instant[] {NOW, NOW.minusSeconds(1)}) {
			User pending = User.create(owner.id(), owner.encryptedEmail(), owner.emailSearchHash(), owner.passwordHash(), NOW.minusSeconds(100))
				.verifyEmail(NOW.minusSeconds(99)).requestWithdrawal(NOW.minusSeconds(60), due);
			authenticate(pending);
			mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token")).andExpect(status().isUnauthorized());
		}
		verifyNoInteractions(profiles, decryptor);
	}
	@Test void invalidExpiredMissingOwnerAndInactiveSessionDoNotReadProfile() throws Exception {
		for (String token : new String[] {"invalid", "expired"}) {
			when(tokens.verify(token)).thenThrow(new com.allermeal.application.auth.UnauthorizedAccessException());
			mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
		}
		authenticate(owner);
		when(users.findById(owner.id())).thenReturn(Optional.empty());
		mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token")).andExpect(status().isUnauthorized());
		authenticate(owner);
		when(refresh.sessionStatus(null)).thenThrow(new com.allermeal.application.auth.UnauthorizedAccessException());
		mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token")
			.header("X-Expected-Session-Id", "session")).andExpect(status().isUnauthorized());
		verifyNoInteractions(profiles, decryptor);
	}
	@Test void missingLockedRowAndPendingDeletedOrStaleSessionCannotDecrypt() throws Exception {
		User pending = owner.requestWithdrawal(NOW.plusSeconds(1), NOW.plusSeconds(600));
		authenticate(pending);
		when(profiles.findForUpdate(owner.id())).thenReturn(Optional.empty());
		mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token")).andExpect(status().isUnauthorized());
		for (AccountProfileDataResult data : new AccountProfileDataResult[] {
			new AccountProfileDataResult("이름", CIPHER, UserStatus.WITHDRAWAL_PENDING, EmailVerificationStatus.VERIFIED, owner.sessionVersion(), true, NOW.plusSeconds(600), UserRole.MEMBER),
			new AccountProfileDataResult("이름", CIPHER, UserStatus.WITHDRAWAL_PENDING, EmailVerificationStatus.VERIFIED, owner.sessionVersion() + 1, false, NOW.plusSeconds(600), UserRole.MEMBER)}) {
			when(profiles.findForUpdate(owner.id())).thenReturn(Optional.of(data));
			mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token")).andExpect(status().isUnauthorized());
		}
		verifyNoInteractions(decryptor);
	}
	@Test void profileContractsRedactPersonalData() {
		org.junit.jupiter.api.Assertions.assertEquals("AccountProfileDataResult[REDACTED]", data(owner, "이름").toString());
		org.junit.jupiter.api.Assertions.assertEquals("AccountProfileResult[REDACTED]", new com.allermeal.application.account.AccountProfileResult("이름", "owner@example.test", UserStatus.ACTIVE, UserRole.MEMBER).toString());
		org.junit.jupiter.api.Assertions.assertEquals("AccountProfileResponse[REDACTED]", new com.allermeal.api.account.response.AccountProfileResponse("이름", "owner@example.test", UserStatus.ACTIVE, UserRole.MEMBER).toString());
	}

	@Test void decryptionFailureReturnsNoIdentity() throws Exception {
		authenticate(owner);
		when(profiles.findForUpdate(owner.id())).thenReturn(Optional.of(data(owner, "이름")));
		when(decryptor.decrypt(any())).thenThrow(new IllegalStateException("이메일 복호화에 실패했습니다."));
		mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
			.andExpect(jsonPath("$.displayName").doesNotExist())
			.andExpect(jsonPath("$.email").doesNotExist());
	}
	@Test void freshDatabaseStateIsRecheckedBeforeDecrypting() throws Exception {
		authenticate(owner);
		for (AccountProfileDataResult data : new AccountProfileDataResult[] {
			new AccountProfileDataResult("이름", CIPHER, UserStatus.WITHDRAWAL_PENDING, EmailVerificationStatus.VERIFIED, owner.sessionVersion(), false, null, UserRole.MEMBER),
			new AccountProfileDataResult("이름", CIPHER, UserStatus.ACTIVE, EmailVerificationStatus.VERIFIED, owner.sessionVersion() + 1, false, null, UserRole.MEMBER),
			new AccountProfileDataResult("이름", CIPHER, UserStatus.ACTIVE, EmailVerificationStatus.VERIFIED, owner.sessionVersion(), true, null, UserRole.MEMBER),
			new AccountProfileDataResult("이름", CIPHER, UserStatus.SUSPENDED, EmailVerificationStatus.VERIFIED, owner.sessionVersion(), false, null, UserRole.MEMBER),
			new AccountProfileDataResult("이름", CIPHER, UserStatus.ACTIVE, EmailVerificationStatus.UNVERIFIED, owner.sessionVersion(), false, null, UserRole.MEMBER) }) {
			when(profiles.findForUpdate(owner.id())).thenReturn(Optional.of(data));
			int expected = data.status() == UserStatus.SUSPENDED || data.emailVerificationStatus() != EmailVerificationStatus.VERIFIED ? 403 : 401;
			mvc.perform(get("/api/v1/account/profile").header("Authorization", "Bearer token")).andExpect(status().is(expected));
		}
		verifyNoInteractions(decryptor);
	}
}
