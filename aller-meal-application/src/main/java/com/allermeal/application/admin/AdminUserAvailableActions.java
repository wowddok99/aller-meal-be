package com.allermeal.application.admin;

public record AdminUserAvailableActions(
	boolean canPromoteToAdmin,
	boolean canSuspend,
	boolean canUnsuspend
) {
}
