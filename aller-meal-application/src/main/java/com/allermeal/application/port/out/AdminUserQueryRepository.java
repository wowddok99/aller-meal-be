package com.allermeal.application.port.out;

import com.allermeal.application.port.out.result.AdminUserQueryPageResult;
import com.allermeal.application.port.out.result.AdminUserQueryResult;
import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserStatus;
import java.util.Optional;

public interface AdminUserQueryRepository {

	AdminUserQueryPageResult findVisibleUsers(
		UserStatus status,
		UserId userId,
		EmailSearchHash emailSearchHash,
		int page,
		int pageSize
	);

	Optional<AdminUserQueryResult> findVisibleUserById(UserId userId);
}
