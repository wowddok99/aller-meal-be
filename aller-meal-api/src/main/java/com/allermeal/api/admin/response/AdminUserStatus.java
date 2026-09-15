package com.allermeal.api.admin.response;

import com.allermeal.domain.user.UserStatus;

public enum AdminUserStatus {

	ACTIVE,
	WITHDRAWAL_PENDING,
	SUSPENDED;

	static AdminUserStatus from(UserStatus status) {
		return switch (status) {
			case ACTIVE -> ACTIVE;
			case WITHDRAWAL_PENDING -> WITHDRAWAL_PENDING;
			case SUSPENDED -> SUSPENDED;
			case DISABLED -> throw new IllegalArgumentException("DISABLED 사용자는 관리자 API 응답에 포함할 수 없습니다.");
		};
	}
}
