package com.allermeal.api.account.response;

import com.allermeal.application.account.AccountWithdrawalResult;
import java.time.Instant;
import java.util.UUID;

public record AccountWithdrawalResponse(
	UUID userId,
	Instant withdrawalRequestedAt,
	Instant withdrawalDueAt,
	int maskedNotificationCount
) {

	public static AccountWithdrawalResponse from(AccountWithdrawalResult result) {
		return new AccountWithdrawalResponse(
			result.userId().value(),
			result.withdrawalRequestedAt(),
			result.withdrawalDueAt(),
			result.maskedNotificationCount());
	}
}
