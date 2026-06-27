package com.allermeal.api.notification;

import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.api.notification.response.NotificationHistoryResponse;
import com.allermeal.application.notification.NotificationHistoryService;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/children/{childId}/notifications")
public final class NotificationHistoryController {

	private final NotificationHistoryService notificationHistoryService;

	public NotificationHistoryController(NotificationHistoryService notificationHistoryService) {
		this.notificationHistoryService = notificationHistoryService;
	}

	@GetMapping
	public NotificationHistoryResponse findByChild(
		HttpServletRequest servletRequest,
		@PathVariable UUID childId,
		@RequestParam(name = "page", defaultValue = "1") int page,
		@RequestParam(name = "pageSize", defaultValue = "20") int pageSize
	) {
		return NotificationHistoryResponse.from(notificationHistoryService.findByChild(
			currentUser(servletRequest).id(), new ChildProfileId(childId), page, pageSize));
	}

	private User currentUser(HttpServletRequest request) {
		return (User) Objects.requireNonNull(request.getAttribute(AuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE));
	}
}
