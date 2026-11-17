package com.allermeal.application.account;

import com.allermeal.application.auth.AccountSuspendedException;
import com.allermeal.application.auth.EmailNotVerifiedException;
import com.allermeal.application.auth.UnauthorizedAccessException;
import com.allermeal.application.port.out.AccountProfileRepository;
import com.allermeal.application.port.out.EmailDecryptor;
import com.allermeal.application.port.out.result.AccountProfileDataResult;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.EncryptedEmail;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserStatus;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

public class AccountProfileService {
	private final AccountProfileRepository repository;
	private final EmailDecryptor decryptor;
	private final Clock clock;

	public AccountProfileService(AccountProfileRepository repository, EmailDecryptor decryptor, Clock clock) {
		this.repository = repository;
		this.decryptor = decryptor;
		this.clock = clock;
	}

	@Transactional
	public AccountProfileResult findProfile(UserId userId, long sessionVersion) {
		AccountProfileDataResult data = repository.findForUpdate(userId).orElseThrow(UnauthorizedAccessException::new);
		if (data.status() == UserStatus.SUSPENDED) throw new AccountSuspendedException();
		if (data.personalDataDeleted() || data.sessionVersion() != sessionVersion
			|| (data.status() != UserStatus.ACTIVE && data.status() != UserStatus.WITHDRAWAL_PENDING)
			|| (data.status() == UserStatus.WITHDRAWAL_PENDING
				&& (data.withdrawalDueAt() == null || !clock.instant().isBefore(data.withdrawalDueAt())))) {
			throw new UnauthorizedAccessException();
		}
		if (data.emailVerificationStatus() != EmailVerificationStatus.VERIFIED) throw new EmailNotVerifiedException();
		return new AccountProfileResult(data.displayName(), decryptor.decrypt(new EncryptedEmail(data.encryptedEmail())), data.status(), data.role());
	}
}
