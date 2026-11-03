package com.allermeal.application.auth;

import java.time.Duration;

public final class RefreshSessionPolicy {

	public static final Duration SLIDING_TTL = Duration.ofDays(14);
	static final Duration ABSOLUTE_TTL = Duration.ofDays(30);

	private RefreshSessionPolicy() {
	}
}
