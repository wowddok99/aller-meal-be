package com.allermeal.application.account;

import com.allermeal.domain.user.UserId;
import java.time.Instant;

public record AccountWithdrawalResult(
	UserId userId,
	Instant withdrawalRequestedAt,
	Instant withdrawalDueAt,
	int maskedNotificationCount
) {
}
