package com.allermeal.application.account;

import com.allermeal.application.port.out.AccountWithdrawalPrivacyRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class ExpiredAccountPersonalDataCleanupService {

	private final AccountWithdrawalPrivacyRepository privacyRepository;
	private final Clock clock;

	public ExpiredAccountPersonalDataCleanupService(
		AccountWithdrawalPrivacyRepository privacyRepository,
		Clock clock
	) {
		this.privacyRepository = Objects.requireNonNull(
			privacyRepository, "AccountWithdrawalPrivacyRepository는 null일 수 없습니다.");
		this.clock = Objects.requireNonNull(clock, "Clock은 null일 수 없습니다.");
	}

	@Transactional
	public ExpiredAccountPersonalDataCleanupResult deleteExpiredPersonalData() {
		Instant now = clock.instant();
		int deletedUserCount = privacyRepository.deleteExpiredPersonalData(now, now);
		return new ExpiredAccountPersonalDataCleanupResult(now, deletedUserCount);
	}
}
