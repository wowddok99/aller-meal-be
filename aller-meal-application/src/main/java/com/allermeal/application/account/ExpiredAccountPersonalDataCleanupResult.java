package com.allermeal.application.account;

import java.time.Instant;

public record ExpiredAccountPersonalDataCleanupResult(
	Instant dueBeforeInclusive,
	int deletedUserCount
) {
}
