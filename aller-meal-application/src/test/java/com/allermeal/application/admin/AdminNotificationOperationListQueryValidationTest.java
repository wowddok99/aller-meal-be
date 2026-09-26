package com.allermeal.application.admin;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allermeal.application.port.out.AdminAuditLogRepository;
import com.allermeal.application.port.out.AdminNotificationReprocessRequestRepository;
import com.allermeal.application.port.out.DeadLetterEventRepository;
import com.allermeal.application.port.out.NotificationRequestRepository;
import com.allermeal.application.port.out.OutboxEventRepository;
import com.allermeal.domain.common.EntityTimestamps;
import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.EncryptedEmail;
import com.allermeal.domain.user.PasswordHash;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AdminNotificationOperationListQueryValidationTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);

	@Test
	void rejectsUnsupportedOutboxAndNotificationFiltersBeforeRepositoryInvocation() {
		AdminNotificationFailureService service = service();

		assertThrows(AdminInvalidNotificationFailureRequestException.class,
			() -> service.findOutboxEvents(admin(), 1, 20, "FAILED", null, null));
		assertThrows(AdminInvalidNotificationFailureRequestException.class,
			() -> service.findNotificationRequests(admin(), 1, 20, "RETRY", null, null, null));
		assertThrows(AdminInvalidNotificationFailureRequestException.class,
			() -> service.findNotificationRequests(admin(), 1, 20, null, "SMS", null, null));
		assertThrows(AdminInvalidNotificationFailureRequestException.class,
			() -> service.findNotificationRequests(admin(), 1, 20, null, null, null, "not-a-uuid"));
	}

	@Test
	void rejectsNonAdminBeforeDeadLetterRepositoryInvocation() {
		assertThrows(AdminAuthorizationException.class,
			() -> service().findDeadLetterEvents(member(), 1, 20, null, null, null));
	}

	private static AdminNotificationFailureService service() {
		return new AdminNotificationFailureService(unavailable(NotificationRequestRepository.class), unavailable(DeadLetterEventRepository.class),
			unavailable(AdminNotificationReprocessRequestRepository.class), unavailable(OutboxEventRepository.class),
			unavailable(AdminAuditLogRepository.class), CLOCK);
	}

	private static User admin() { return user(UserRole.ADMIN); }

	private static User member() { return user(UserRole.MEMBER); }

	private static User user(UserRole role) {
		return User.restoreFromPersistence(new UserId(UUID.randomUUID()),
			new EncryptedEmail("v1:k:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA=="), new EmailSearchHash("a".repeat(64)),
			new PasswordHash("hash"), role, UserStatus.ACTIVE, EmailVerificationStatus.VERIFIED,
			new EntityTimestamps(CLOCK.instant(), CLOCK.instant()), 0);
	}

	@SuppressWarnings("unchecked")
	private static <T> T unavailable(Class<T> type) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
			(proxy, method, arguments) -> { throw new AssertionError("repository invocation: " + method.getName()); });
	}
}
