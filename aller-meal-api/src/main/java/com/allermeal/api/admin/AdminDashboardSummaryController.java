package com.allermeal.api.admin;

import com.allermeal.api.admin.response.AdminDashboardSummaryResponse;
import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.application.admin.AdminDashboardSummaryService;
import com.allermeal.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public final class AdminDashboardSummaryController {

	private final AdminDashboardSummaryService summaryService;

	public AdminDashboardSummaryController(AdminDashboardSummaryService summaryService) {
		this.summaryService = summaryService;
	}

	@GetMapping("/dashboard-summary")
	public AdminDashboardSummaryResponse getSummary(HttpServletRequest request) {
		return AdminDashboardSummaryResponse.from(summaryService.getSummary(currentUser(request)));
	}

	private User currentUser(HttpServletRequest request) {
		return (User) Objects.requireNonNull(request.getAttribute(AuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE));
	}
}
