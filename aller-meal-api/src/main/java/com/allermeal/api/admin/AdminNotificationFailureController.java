package com.allermeal.api.admin;

import com.allermeal.api.admin.response.AdminDeadLetterEventPageResponse;
import com.allermeal.api.admin.response.AdminFailedNotificationPageResponse;
import com.allermeal.api.admin.response.AdminNotificationRequestPageResponse;
import com.allermeal.api.admin.response.AdminNotificationReprocessResponse;
import com.allermeal.api.admin.response.AdminOutboxEventPageResponse;
import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.application.admin.AdminNotificationFailureService;
import com.allermeal.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
	@Operation(deprecated = true)
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
		@Parameter(schema = @Schema(minimum = "1", defaultValue = "1"))
		@RequestParam(name = "page", defaultValue = "1") int page,
		@Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
		@RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
		@Parameter(schema = @Schema(allowableValues = {"PENDING", "REPROCESSED"}))
		@RequestParam(required = false) String status,
		@Parameter(schema = @Schema(maxLength = 100)) @RequestParam(required = false) String eventType,
		@Parameter(schema = @Schema(maxLength = 100)) @RequestParam(required = false) String query
	) {
		return AdminDeadLetterEventPageResponse.from(notificationFailureService.findDeadLetterEvents(
			currentUser(request), page, pageSize, status, eventType, query));
	}

	@GetMapping("/outbox-events")
	public AdminOutboxEventPageResponse findOutboxEvents(
		HttpServletRequest request, @Parameter(schema = @Schema(minimum = "1", defaultValue = "1"))
		@RequestParam(name = "page", defaultValue = "1") int page,
		@Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
		@RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
		@Parameter(schema = @Schema(allowableValues = {"PENDING", "PUBLISHED"}))
		@RequestParam(required = false) String status,
		@Parameter(schema = @Schema(maxLength = 100)) @RequestParam(required = false) String eventType,
		@Parameter(schema = @Schema(maxLength = 100)) @RequestParam(required = false) String query
	) {
		return AdminOutboxEventPageResponse.from(notificationFailureService.findOutboxEvents(
			currentUser(request), page, pageSize, status, eventType, query));
	}

	@GetMapping("/notification-requests")
	public AdminNotificationRequestPageResponse findNotificationRequests(
		HttpServletRequest request, @Parameter(schema = @Schema(minimum = "1", defaultValue = "1"))
		@RequestParam(name = "page", defaultValue = "1") int page,
		@Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
		@RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
		@Parameter(schema = @Schema(allowableValues = {"PENDING", "SENDING", "RETRY_PENDING", "SENT", "FAILED", "CANCELED"}))
		@RequestParam(required = false) String status,
		@Parameter(schema = @Schema(allowableValues = "EMAIL")) @RequestParam(required = false) String channel,
		@Parameter(schema = @Schema(allowableValues = {"RISK_DETECTED", "NO_RISK", "RISK_UNKNOWN", "RISK_LABELING_FAILED", "RISK_PENDING", "NO_MEAL"}))
		@RequestParam(required = false) String reason,
		@Parameter(schema = @Schema(format = "uuid")) @RequestParam(required = false) String query
	) {
		return AdminNotificationRequestPageResponse.from(notificationFailureService.findNotificationRequests(
			currentUser(request), page, pageSize, status, channel, reason, query));
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
