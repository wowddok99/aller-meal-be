package com.allermeal.application.port.out;

import com.allermeal.application.port.out.result.AccountProfileDataResult;
import com.allermeal.domain.user.UserId;
import java.util.Optional;

public interface AccountProfileRepository {
	Optional<AccountProfileDataResult> findForUpdate(UserId userId);
}
