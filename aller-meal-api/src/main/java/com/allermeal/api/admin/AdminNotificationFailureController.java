package com.allermeal.api.admin;

import com.allermeal.api.admin.response.AdminDeadLetterEventPageResponse;
import com.allermeal.api.admin.response.AdminFailedNotificationPageResponse;
import com.allermeal.api.admin.response.AdminNotificationReprocessResponse;
import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.application.admin.AdminNotificationFailureService;
import com.allermeal.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public final class AdminNotificationFailureController {

	private final AdminNotificationFailureService notificationFailureService;

	public AdminNotificationFailureController(AdminNotificationFailureService notificationFailureService) {
		this.notificationFailureService = notificationFailureService;
	}

	@GetMapping("/notifications/failed")
	public AdminFailedNotificationPageResponse findFailedNotifications(
		HttpServletRequest request,
		@RequestParam(name = "page", defaultValue = "1") int page,
		@RequestParam(name = "pageSize", defaultValue = "20") int pageSize
	) {
		return AdminFailedNotificationPageResponse.from(notificationFailureService.findFailedNotifications(
			currentUser(request), page, pageSize));
	}

	@GetMapping("/notification-dlq-events")
	public AdminDeadLetterEventPageResponse findDeadLetterEvents(
		HttpServletRequest request,
		@RequestParam(name = "page", defaultValue = "1") int page,
		@RequestParam(name = "pageSize", defaultValue = "20") int pageSize
	) {
		return AdminDeadLetterEventPageResponse.from(notificationFailureService.findDeadLetterEvents(
			currentUser(request), page, pageSize));
	}

	@PostMapping("/notification-dlq-events/{deadLetterEventId}/reprocess")
	public AdminNotificationReprocessResponse reprocessDeadLetterEvent(
		HttpServletRequest request,
		@PathVariable UUID deadLetterEventId,
		@RequestHeader("Idempotency-Key") String idempotencyKey
	) {
		return AdminNotificationReprocessResponse.from(notificationFailureService.reprocessDeadLetterEvent(
			currentUser(request), deadLetterEventId, idempotencyKey));
	}

	private User currentUser(HttpServletRequest request) {
		return (User) Objects.requireNonNull(request.getAttribute(AuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE));
	}
}
