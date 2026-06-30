package com.allermeal.application.admin;

import com.allermeal.application.port.out.AdminDashboardSummaryRepository;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class AdminDashboardSummaryService {

	private static final Duration SUMMARY_CACHE_TTL = Duration.ofSeconds(60);

	private final AdminDashboardSummaryRepository summaryRepository;
	private final Clock clock;

	public AdminDashboardSummaryService(AdminDashboardSummaryRepository summaryRepository, Clock clock) {
		this.summaryRepository = summaryRepository;
		this.clock = clock;
	}

	public AdminDashboardSummaryResult getSummary(User actor) {
		requireAdmin(actor);
		Instant now = clock.instant();
		return summaryRepository.findOrCreate(now.minus(SUMMARY_CACHE_TTL), now);
	}

	private void requireAdmin(User actor) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new AdminAuthorizationException();
		}
	}
}
