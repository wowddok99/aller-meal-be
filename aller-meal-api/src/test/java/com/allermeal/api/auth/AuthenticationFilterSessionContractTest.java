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
		new EmailSearchHash("0".repeat(64)), new PasswordHash("password-hash"), now);
	private final AccessTokenIssuer issuer = mock(AccessTokenIssuer.class);
	private final UserRepository users = mock(UserRepository.class);
	private final RefreshService refresh = mock(RefreshService.class);
	private final AuthenticationFilter filter = new AuthenticationFilter(issuer, users, refresh);

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
