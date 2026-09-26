package com.allermeal.api.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allermeal.api.auth.AuthCookieWriter;
import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.api.auth.AuthenticationRequestProtectionFilter;
import com.allermeal.api.error.ApiExceptionHandler;
import com.allermeal.api.error.TraceIdFilter;
import com.allermeal.application.admin.AdminAuthorizationException;
import com.allermeal.application.admin.AdminCollectionFailureService;
import com.allermeal.application.admin.AdminCollectionJobPageResult;
import com.allermeal.application.admin.AdminDeadLetterEventPageResult;
import com.allermeal.application.admin.AdminExternalApiLogPageResult;
import com.allermeal.application.admin.AdminInvalidCollectionRequestException;
import com.allermeal.application.admin.AdminInvalidNotificationFailureRequestException;
import com.allermeal.application.admin.AdminMealItemLabelingPageResult;
import com.allermeal.application.admin.AdminNotificationFailureService;
import com.allermeal.application.admin.AdminNotificationRequestPageResult;
import com.allermeal.application.admin.AdminOutboxEventPageResult;
import com.allermeal.application.auth.AccessTokenClaims;
import com.allermeal.application.port.out.AccessTokenIssuer;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.EncryptedEmail;
import com.allermeal.domain.user.PasswordHash;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

final class AdminOperationListHttpContractTest {

	private static final Instant NOW = Instant.parse("2026-09-26T00:00:00Z");

	@Test
	void allUnifiedAndLegacyListEndpointsReturnFilteredPageContractsForAnAdmin() throws Exception {
		Fixture fixture = Fixture.create();

		fixture.mvc().perform(get("/api/v1/admin/collection-jobs")
				.cookie(accessCookie("admin-access"))
				.param("status", "FAILED").param("page", "2").param("pageSize", "20"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.pageSize").value(20)).andExpect(jsonPath("$.totalCount").value(21))
			.andExpect(jsonPath("$.items").isEmpty());
		fixture.mvc().perform(get("/api/v1/admin/meal-item-labelings")
				.cookie(accessCookie("admin-access")).param("status", "LABELING_FAILED"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
		fixture.mvc().perform(get("/api/v1/admin/external-api-logs")
				.cookie(accessCookie("admin-access")).param("provider", "NEIS"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
		fixture.mvc().perform(get("/api/v1/admin/outbox-events")
				.cookie(accessCookie("admin-access")).param("status", "PENDING"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
		fixture.mvc().perform(get("/api/v1/admin/notification-dlq-events")
				.cookie(accessCookie("admin-access")).param("status", "PENDING"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
		fixture.mvc().perform(get("/api/v1/admin/notification-requests")
				.cookie(accessCookie("admin-access")).param("status", "FAILED"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));

		fixture.mvc().perform(get("/api/v1/admin/collection-jobs/failed")
				.cookie(accessCookie("admin-access")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
		fixture.mvc().perform(get("/api/v1/admin/notifications/failed")
				.cookie(accessCookie("admin-access")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
	}

	@Test
	void listEndpointsPreserveInvalidRequestUnauthenticatedAndNonAdminContracts() throws Exception {
		Fixture fixture = Fixture.create();

		for (String endpoint : List.of("/api/v1/admin/collection-jobs", "/api/v1/admin/meal-item-labelings",
			"/api/v1/admin/external-api-logs", "/api/v1/admin/outbox-events",
			"/api/v1/admin/notification-dlq-events", "/api/v1/admin/notification-requests")) {
			fixture.mvc().perform(get(endpoint))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
			fixture.mvc().perform(get(endpoint).cookie(accessCookie("member-access")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		}
		fixture.mvc().perform(get("/api/v1/admin/collection-jobs")
				.cookie(accessCookie("admin-access")).param("status", "NOT_A_STATUS"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
		fixture.mvc().perform(get("/api/v1/admin/meal-item-labelings")
				.cookie(accessCookie("admin-access")).param("status", "NOT_A_STATUS"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
		fixture.mvc().perform(get("/api/v1/admin/outbox-events")
				.cookie(accessCookie("admin-access")).param("status", "NOT_A_STATUS"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
		fixture.mvc().perform(get("/api/v1/admin/notification-dlq-events")
				.cookie(accessCookie("admin-access")).param("status", "NOT_A_STATUS"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
		fixture.mvc().perform(get("/api/v1/admin/notification-requests")
				.cookie(accessCookie("admin-access")).param("status", "NOT_A_STATUS"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
		fixture.mvc().perform(get("/api/v1/admin/external-api-logs")
				.cookie(accessCookie("admin-access")).param("query", "x".repeat(101)))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
	}

	private static Cookie accessCookie(String value) {
		return new Cookie(AuthCookieWriter.ACCESS_TOKEN_COOKIE, value);
	}

	private static final class Fixture {

		private final MockMvc mvc;

		private Fixture(MockMvc mvc) {
			this.mvc = mvc;
		}

		static Fixture create() {
			User admin = user(UserRole.ADMIN, "11111111-1111-1111-1111-111111111111");
			User member = user(UserRole.MEMBER, "22222222-2222-2222-2222-222222222222");
			Map<UserId, User> users = Map.of(admin.id(), admin, member.id(), member);
			AccessTokenIssuer issuer = new TokenIssuer(Map.of("admin-access", admin, "member-access", member));
			UserRepository usersById = new UserRepository() {
				@Override public User save(User user) { return user; }
				@Override public Optional<User> findById(UserId userId) { return Optional.ofNullable(users.get(userId)); }
				@Override public Optional<User> findByEmailSearchHash(EmailSearchHash hash) { return Optional.empty(); }
				@Override public boolean existsByEmailSearchHash(EmailSearchHash hash) { return false; }
				@Override public boolean existsByRole(UserRole role) { return false; }
			};
			AdminCollectionFailureService collectionService = org.mockito.Mockito.mock(AdminCollectionFailureService.class);
			AdminNotificationFailureService notificationService = org.mockito.Mockito.mock(AdminNotificationFailureService.class);
			when(collectionService.findCollectionJobs(any(), any(Integer.class), any(Integer.class), any(), any(), any(), any(), any()))
				.thenReturn(new AdminCollectionJobPageResult(List.of(), 2, 20, 21));
			when(collectionService.findMealItemLabelings(any(), any(Integer.class), any(Integer.class), any(), any(), any(), any(), any()))
				.thenReturn(new AdminMealItemLabelingPageResult(List.of(), 1, 20, 0));
			when(collectionService.findExternalApiLogs(any(), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
				.thenReturn(new AdminExternalApiLogPageResult(List.of(), 1, 20, 0));
			when(collectionService.findFailedCollectionJobs(any(), any(Integer.class), any(Integer.class)))
				.thenReturn(new com.allermeal.application.admin.AdminFailedCollectionJobPageResult(List.of(), 1, 20, 0));
			when(notificationService.findOutboxEvents(any(), any(Integer.class), any(Integer.class), any(), any(), any()))
				.thenReturn(new AdminOutboxEventPageResult(List.of(), 1, 20, 0));
			when(notificationService.findDeadLetterEvents(any(), any(Integer.class), any(Integer.class), any(), any(), any()))
				.thenReturn(new AdminDeadLetterEventPageResult(List.of(), 1, 20, 0));
			when(notificationService.findNotificationRequests(any(), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
				.thenReturn(new AdminNotificationRequestPageResult(List.of(), 1, 20, 0));
			when(notificationService.findFailedNotifications(any(), any(Integer.class), any(Integer.class)))
				.thenReturn(new com.allermeal.application.admin.AdminFailedNotificationPageResult(List.of(), 1, 20, 0));
			when(collectionService.findCollectionJobs(org.mockito.ArgumentMatchers.eq(member), any(Integer.class), any(Integer.class), any(), any(), any(), any(), any()))
				.thenThrow(new AdminAuthorizationException());
			when(collectionService.findMealItemLabelings(org.mockito.ArgumentMatchers.eq(member), any(Integer.class), any(Integer.class), any(), any(), any(), any(), any()))
				.thenThrow(new AdminAuthorizationException());
			when(collectionService.findExternalApiLogs(org.mockito.ArgumentMatchers.eq(member), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
				.thenThrow(new AdminAuthorizationException());
			when(notificationService.findOutboxEvents(org.mockito.ArgumentMatchers.eq(member), any(Integer.class), any(Integer.class), any(), any(), any()))
				.thenThrow(new AdminAuthorizationException());
			when(notificationService.findDeadLetterEvents(org.mockito.ArgumentMatchers.eq(member), any(Integer.class), any(Integer.class), any(), any(), any()))
				.thenThrow(new AdminAuthorizationException());
			when(notificationService.findNotificationRequests(org.mockito.ArgumentMatchers.eq(member), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
				.thenThrow(new AdminAuthorizationException());
			when(collectionService.findCollectionJobs(org.mockito.ArgumentMatchers.eq(admin), any(Integer.class), any(Integer.class),
				org.mockito.ArgumentMatchers.eq("NOT_A_STATUS"), any(), any(), any(), any()))
				.thenThrow(new AdminInvalidCollectionRequestException());
			when(collectionService.findMealItemLabelings(org.mockito.ArgumentMatchers.eq(admin), any(Integer.class), any(Integer.class),
				org.mockito.ArgumentMatchers.eq("NOT_A_STATUS"), any(), any(), any(), any()))
				.thenThrow(new AdminInvalidCollectionRequestException());
			when(collectionService.findExternalApiLogs(org.mockito.ArgumentMatchers.eq(admin), any(Integer.class), any(Integer.class), any(), any(), any(),
				org.mockito.ArgumentMatchers.eq("x".repeat(101))))
				.thenThrow(new AdminInvalidCollectionRequestException());
			when(notificationService.findOutboxEvents(org.mockito.ArgumentMatchers.eq(admin), any(Integer.class), any(Integer.class),
				org.mockito.ArgumentMatchers.eq("NOT_A_STATUS"), any(), any()))
				.thenThrow(new AdminInvalidNotificationFailureRequestException());
			when(notificationService.findDeadLetterEvents(org.mockito.ArgumentMatchers.eq(admin), any(Integer.class), any(Integer.class),
				org.mockito.ArgumentMatchers.eq("NOT_A_STATUS"), any(), any()))
				.thenThrow(new AdminInvalidNotificationFailureRequestException());
			when(notificationService.findNotificationRequests(org.mockito.ArgumentMatchers.eq(admin), any(Integer.class), any(Integer.class),
				org.mockito.ArgumentMatchers.eq("NOT_A_STATUS"), any(), any(), any()))
				.thenThrow(new AdminInvalidNotificationFailureRequestException());

			MockMvc mvc = MockMvcBuilders.standaloneSetup(
				new AdminCollectionFailureController(collectionService),
				new AdminNotificationFailureController(notificationService))
				.setControllerAdvice(new ApiExceptionHandler())
				.addFilters(new TraceIdFilter(),
					new AuthenticationRequestProtectionFilter(org.mockito.Mockito.mock(StringRedisTemplate.class), 20, Duration.ofMinutes(1), "http://localhost:3000"),
					new AuthenticationFilter(issuer, usersById))
				.build();
			return new Fixture(mvc);
		}

		MockMvc mvc() { return mvc; }
	}

	private static User user(UserRole role, String id) {
		if (role == UserRole.ADMIN) {
			return User.createAdmin(new UserId(UUID.fromString(id)), new EncryptedEmail("v1:test:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA=="),
				new EmailSearchHash("0".repeat(64)), new PasswordHash("password-hash"), NOW);
		}
		return User.restoreFromPersistence(new UserId(UUID.fromString(id)), new EncryptedEmail("v1:test:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA=="),
			new EmailSearchHash("1".repeat(64)), new PasswordHash("password-hash"), UserRole.MEMBER,
			com.allermeal.domain.user.UserStatus.ACTIVE, com.allermeal.domain.user.EmailVerificationStatus.VERIFIED,
			com.allermeal.domain.common.EntityTimestamps.createdAt(NOW), 0);
	}

	private static final class TokenIssuer implements AccessTokenIssuer {

		private final Map<String, AccessTokenClaims> claims = new HashMap<>();

		private TokenIssuer(Map<String, User> users) {
			users.forEach((token, user) -> claims.put(token, new AccessTokenClaims(user.id(), user.role(), user.status(),
				user.emailVerificationStatus(), user.sessionVersion(), NOW.plus(Duration.ofMinutes(15)))));
		}

		@Override public String issue(User user, Duration ttl) { throw new UnsupportedOperationException(); }
		@Override public AccessTokenClaims verify(String token) {
			AccessTokenClaims result = claims.get(token);
			if (result == null) throw new IllegalArgumentException("invalid access token");
			return result;
		}
	}
}
