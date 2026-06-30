package com.allermeal.application.port.out;

import com.allermeal.domain.user.UserId;
import java.time.Instant;

public interface AccountWithdrawalPrivacyRepository {

	int maskNotificationPersonalData(UserId userId, Instant maskedAt);

	int deleteExpiredPersonalData(Instant dueBeforeInclusive, Instant deletedAt);
}
