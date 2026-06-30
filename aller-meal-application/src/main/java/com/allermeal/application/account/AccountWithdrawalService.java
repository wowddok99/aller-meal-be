package com.allermeal.application.account;

import com.allermeal.application.auth.UnauthorizedAccessException;
import com.allermeal.application.port.out.AccountWithdrawalPrivacyRepository;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

public class AccountWithdrawalService {

	private static final Duration WITHDRAWAL_GRACE_PERIOD = Duration.ofDays(7);

	private final UserRepository userRepository;
	private final AccountWithdrawalPrivacyRepository privacyRepository;
	private final Clock clock;

	public AccountWithdrawalService(
		UserRepository userRepository,
		AccountWithdrawalPrivacyRepository privacyRepository,
		Clock clock
	) {
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository는 null일 수 없습니다.");
		this.privacyRepository = Objects.requireNonNull(
			privacyRepository, "AccountWithdrawalPrivacyRepository는 null일 수 없습니다.");
		this.clock = Objects.requireNonNull(clock, "Clock은 null일 수 없습니다.");
	}

	@Transactional
	public AccountWithdrawalResult requestWithdrawal(UserId userId) {
		Objects.requireNonNull(userId, "사용자 ID는 null일 수 없습니다.");
		User user = userRepository.findById(userId).orElseThrow(UnauthorizedAccessException::new);
		Instant now = clock.instant();
		User pending = user.requestWithdrawal(now, now.plus(WITHDRAWAL_GRACE_PERIOD));
		User saved = userRepository.save(pending);
		int maskedNotificationCount = privacyRepository.maskNotificationPersonalData(saved.id(), now);
		return new AccountWithdrawalResult(
			saved.id(), saved.withdrawalRequestedAt(), saved.withdrawalDueAt(), maskedNotificationCount);
	}

	@Transactional
	public void cancelWithdrawal(UserId userId) {
		Objects.requireNonNull(userId, "사용자 ID는 null일 수 없습니다.");
		User user = userRepository.findById(userId).orElseThrow(UnauthorizedAccessException::new);
		try {
			userRepository.save(user.cancelWithdrawal(clock.instant()));
		} catch (IllegalStateException | OptimisticLockingFailureException exception) {
			throw new AccountWithdrawalConflictException();
		}
	}

}
