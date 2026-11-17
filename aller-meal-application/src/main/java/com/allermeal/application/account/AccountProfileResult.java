package com.allermeal.application.account;

import com.allermeal.domain.user.UserStatus;
import com.allermeal.domain.user.UserRole;

public record AccountProfileResult(String displayName, String email, UserStatus userStatus, UserRole role) {
	@Override
	public String toString() { return "AccountProfileResult[REDACTED]"; }
}
