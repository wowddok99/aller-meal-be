package com.allermeal.api.admin;

import com.allermeal.api.admin.response.AdminExternalApiLogPageResponse;
import com.allermeal.api.admin.response.AdminFailedCollectionJobPageResponse;
import com.allermeal.api.admin.response.AdminRecollectionResponse;
import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.application.admin.AdminCollectionFailureService;
import com.allermeal.domain.collection.CollectionJobId;
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
public final class AdminCollectionFailureController {

	private final AdminCollectionFailureService collectionFailureService;

	public AdminCollectionFailureController(AdminCollectionFailureService collectionFailureService) {
		this.collectionFailureService = collectionFailureService;
	}

	@GetMapping("/collection-jobs/failed")
	public AdminFailedCollectionJobPageResponse findFailedCollectionJobs(
		HttpServletRequest request,
		@RequestParam(name = "page", defaultValue = "1") int page,
		@RequestParam(name = "pageSize", defaultValue = "20") int pageSize
	) {
		return AdminFailedCollectionJobPageResponse.from(collectionFailureService.findFailedCollectionJobs(
			currentUser(request), page, pageSize));
	}

	@GetMapping("/external-api-logs")
	public AdminExternalApiLogPageResponse findExternalApiLogs(
		HttpServletRequest request,
		@RequestParam(name = "page", defaultValue = "1") int page,
		@RequestParam(name = "pageSize", defaultValue = "20") int pageSize
	) {
		return AdminExternalApiLogPageResponse.from(collectionFailureService.findExternalApiLogs(
			currentUser(request), page, pageSize));
	}

	@PostMapping("/collection-jobs/{collectionJobId}/recollection")
	public AdminRecollectionResponse requestRecollection(
		HttpServletRequest request,
		@PathVariable UUID collectionJobId,
		@RequestHeader("Idempotency-Key") String idempotencyKey
	) {
		return AdminRecollectionResponse.from(collectionFailureService.requestRecollection(
			currentUser(request), new CollectionJobId(collectionJobId), idempotencyKey));
	}

	private User currentUser(HttpServletRequest request) {
		return (User) Objects.requireNonNull(request.getAttribute(AuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE));
	}
}
