package com.allermeal.application.admin;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.ExternalApiLogRepository;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.AdminAuditLogRepository;
import com.allermeal.application.port.out.AdminRecollectionRequestRepository;
import com.allermeal.application.port.out.MealCollectionDispatcher;
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

final class AdminOperationListQueryValidationTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);

	@Test
	void rejectsInvalidCollectionAndLabelingFiltersBeforeRepositoryInvocation() {
		AdminCollectionFailureService service = new AdminCollectionFailureService(
			unavailable(CollectionJobRepository.class), unavailable(ExternalApiLogRepository.class), unavailable(MealRepository.class),
			unavailable(AdminRecollectionRequestRepository.class), unavailable(MealCollectionDispatcher.class),
			unavailable(AdminAuditLogRepository.class), CLOCK);

		assertThrows(AdminInvalidCollectionRequestException.class,
			() -> service.findCollectionJobs(admin(), 1, 20, "FAILED_OR_PENDING", null, null, null, null));
		assertThrows(AdminInvalidCollectionRequestException.class,
			() -> service.findMealItemLabelings(admin(), 1, 20, "RUNNING", null, null, null, null));
		assertThrows(AdminInvalidCollectionRequestException.class,
			() -> service.findCollectionJobs(admin(), 1, 101, null, null, null, null, null));
		assertThrows(AdminInvalidCollectionRequestException.class,
			() -> service.findExternalApiLogs(admin(), 1, 20, null, null, null, "x".repeat(101)));
	}

	@Test
	void rejectsNonAdminBeforeAnyListRepositoryInvocation() {
		AdminCollectionFailureService service = new AdminCollectionFailureService(
			unavailable(CollectionJobRepository.class), unavailable(ExternalApiLogRepository.class), unavailable(MealRepository.class),
			unavailable(AdminRecollectionRequestRepository.class), unavailable(MealCollectionDispatcher.class),
			unavailable(AdminAuditLogRepository.class), CLOCK);

		assertThrows(AdminAuthorizationException.class,
			() -> service.findCollectionJobs(member(), 1, 20, null, null, null, null, null));
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
