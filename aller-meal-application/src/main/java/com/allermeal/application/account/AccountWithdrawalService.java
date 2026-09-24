package com.allermeal.application.account;

import com.allermeal.application.auth.UnauthorizedAccessException;
import com.allermeal.application.port.out.AccountWithdrawalPrivacyRepository;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
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
		try {
			int maskedNotificationCount = privacyRepository.maskNotificationPersonalData(user.id(), now);
			User pending = user.requestWithdrawal(
				now, now.plus(WITHDRAWAL_GRACE_PERIOD), maskedNotificationCount);
			return toResult(userRepository.save(pending));
		} catch (IllegalStateException | OptimisticLockingFailureException exception) {
			throw new AccountWithdrawalConflictException();
		}
	}

	@Transactional(readOnly = true)
	public Optional<AccountWithdrawalResult> findWithdrawal(UserId userId) {
		Objects.requireNonNull(userId, "사용자 ID는 null일 수 없습니다.");
		User user = userRepository.findById(userId).orElseThrow(UnauthorizedAccessException::new);
		if (user.status() != UserStatus.WITHDRAWAL_PENDING) {
			return Optional.empty();
		}
		return Optional.of(toResult(user));
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

	private AccountWithdrawalResult toResult(User user) {
		return new AccountWithdrawalResult(
			user.id(), user.withdrawalRequestedAt(), user.withdrawalDueAt(), user.withdrawalMaskedNotificationCount());
	}

}
