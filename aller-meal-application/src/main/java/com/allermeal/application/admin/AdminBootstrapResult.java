package com.allermeal.application.admin;

import com.allermeal.domain.user.UserId;

public record AdminBootstrapResult(
	boolean changed,
	UserId userId,
	String action
) {
}
