package com.allermeal.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.allermeal.application.auth.AccessTokenClaims;
import com.allermeal.application.auth.RefreshService;
import com.allermeal.application.auth.RefreshSessionStatusResult;
import com.allermeal.application.auth.UnauthorizedAccessException;
import com.allermeal.application.port.out.AccessTokenIssuer;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.domain.user.*;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

final class AuthenticationFilterSessionContractTest {
	private final Instant now = Instant.parse("2026-11-03T00:00:00Z");
	private final User user = User.createAdmin(new UserId(UUID.randomUUID()),
		new EncryptedEmail("v1:test:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA=="),
		new EmailSearchHash("0".repeat(64)), new PasswordHash("password-hash"), now.minusSeconds(120));
	private final AccessTokenIssuer issuer = mock(AccessTokenIssuer.class);
	private final UserRepository users = mock(UserRepository.class);
	private final RefreshService refresh = mock(RefreshService.class);
	private final AuthenticationFilter filter = new AuthenticationFilter(issuer, users, refresh, java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));

	@BeforeEach
	void setUp() {
		when(issuer.verify("access")).thenReturn(new AccessTokenClaims(user.id(), user.role(), user.status(),
			user.emailVerificationStatus(), user.sessionVersion(), now.plusSeconds(900), "session-a"));
		when(users.findById(user.id())).thenReturn(Optional.of(user));
	}

	@Test
	void expectedSessionRequiresMatchingActiveFamilyAndUser() throws Exception {
		when(refresh.sessionStatus("refresh")).thenReturn(new RefreshSessionStatusResult("session-a", user.id().value()));
		MockHttpServletRequest request = request("session-a", "refresh");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(request, response, chain);
		assertEquals(200, response.getStatus());
		assertSame(user, request.getAttribute(AuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE));
		assertNotNull(chain.getRequest());
	}

	@Test
	void legacyRequestDoesNotConsultRefreshAndStillAcceptsAccess() throws Exception {
		MockHttpServletRequest request = request(null, null);
		MockFilterChain chain = new MockFilterChain();
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, chain);
		assertEquals(200, response.getStatus());
		assertNotNull(chain.getRequest());
		verifyNoInteractions(refresh);
	}

	@Test
	void mismatchedSignedSessionOrDifferentFamilyOrUserIsRejected() throws Exception {
		assertRejected(request("session-b", "refresh"), 401, "SESSION_INACTIVE");
		verifyNoInteractions(refresh);
		when(refresh.sessionStatus("refresh")).thenReturn(new RefreshSessionStatusResult("session-b", user.id().value()));
		assertRejected(request("session-a", "refresh"), 401, "SESSION_INACTIVE");
		when(refresh.sessionStatus("refresh")).thenReturn(new RefreshSessionStatusResult("session-a", UUID.randomUUID()));
		assertRejected(request("session-a", "refresh"), 401, "SESSION_INACTIVE");
	}

	@Test
	void revokedOrMissingRefreshIsNotTreatedAsAccessExpiry() throws Exception {
		when(refresh.sessionStatus("revoked")).thenThrow(new UnauthorizedAccessException());
		when(refresh.sessionStatus(null)).thenThrow(new UnauthorizedAccessException());
		assertRejected(request("session-a", "revoked"), 401, "SESSION_INACTIVE");
		assertRejected(request("session-a", null), 401, "SESSION_INACTIVE");
	}

	@Test
	void storageFailureIsRetryableAndFailsClosed() throws Exception {
		when(refresh.sessionStatus("refresh")).thenThrow(new DataAccessResourceFailureException("unavailable"));
		assertRejected(request("session-a", "refresh"), 503, "AUTH_TEMPORARILY_UNAVAILABLE");
	}

	@Test
	void pendingCanReadProfileButOtherNonWithdrawalPathsRemainForbidden() throws Exception {
		User pending = user.requestWithdrawal(now.minusSeconds(60), now.plusNanos(1));
		when(users.findById(user.id())).thenReturn(Optional.of(pending));
		for (String path : new String[] { "/api/v1/account/profile/extra", "/api/v1/children", "/api/v1/account/withdrawal/extra" }) {
			MockHttpServletRequest request = request(null, null);
			request.setRequestURI(path);
			assertRejected(request, 403, "ACCOUNT_WITHDRAWAL_PENDING");
		}
		MockHttpServletRequest profile = request(null, null);
		profile.setRequestURI("/api/v1/account/profile");
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(profile, new MockHttpServletResponse(), chain);
		assertNotNull(chain.getRequest());
		for (String method : new String[] {"HEAD", "POST", "DELETE", "PUT", "PATCH", "OPTIONS"}) {
			MockHttpServletRequest writeProfile = request(null, null);
			writeProfile.setRequestURI("/api/v1/account/profile");
			writeProfile.setMethod(method);
			assertRejected(writeProfile, 403, "ACCOUNT_WITHDRAWAL_PENDING");
		}
		MockHttpServletRequest unsupportedMethod = request(null, null);
		unsupportedMethod.setMethod("PATCH");
		assertRejected(unsupportedMethod, 403, "ACCOUNT_WITHDRAWAL_PENDING");
	}

	@Test
	void invalidExpiredDisabledVersionMismatchAndDeadlineRemainUnauthorized() throws Exception {
		MockHttpServletRequest profile = request(null, null);
		profile.setRequestURI("/api/v1/account/profile");
		for (User rejected : new User[] {
			user.disable(now),
			user.requestWithdrawal(now.minusSeconds(60), now),
			user.requestWithdrawal(now.minusSeconds(60), now.minusNanos(1)) }) {
			when(users.findById(user.id())).thenReturn(Optional.of(rejected));
			assertRejected(profile, 401, "UNAUTHORIZED");
		}
		User pending = user.requestWithdrawal(now.minusSeconds(60), now.plusSeconds(60));
		when(users.findById(user.id())).thenReturn(Optional.of(pending));
		when(issuer.verify("access")).thenReturn(new AccessTokenClaims(user.id(), user.role(), pending.status(),
			user.emailVerificationStatus(), user.sessionVersion() + 1, now.plusSeconds(900), "session-a"));
		assertRejected(profile, 401, "UNAUTHORIZED");
		when(issuer.verify("access")).thenThrow(new IllegalArgumentException("유효하지 않은 토큰"));
		assertRejected(profile, 401, "UNAUTHORIZED");
		doThrow(new IllegalArgumentException("만료된 토큰")).when(issuer).verify("access");
		assertRejected(profile, 401, "UNAUTHORIZED");
	}

	private void assertRejected(MockHttpServletRequest request, int status, String code) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(request, response, chain);
		assertEquals(status, response.getStatus());
		assertTrue(response.getContentAsString().contains("\"code\":\"" + code + "\""));
		assertNull(chain.getRequest());
	}

	private MockHttpServletRequest request(String expected, String refreshToken) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/account/withdrawal");
		request.addHeader("Authorization", "Bearer access");
		if (expected != null) request.addHeader("X-Expected-Session-Id", expected);
		if (refreshToken != null) request.setCookies(new Cookie(AuthCookieWriter.REFRESH_TOKEN_COOKIE, refreshToken));
		return request;
	}
}
