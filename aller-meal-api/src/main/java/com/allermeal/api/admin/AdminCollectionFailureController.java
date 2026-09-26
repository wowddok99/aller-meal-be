package com.allermeal.api.admin;

import com.allermeal.api.admin.response.AdminExternalApiLogPageResponse;
import com.allermeal.api.admin.response.AdminCollectionJobPageResponse;
import com.allermeal.api.admin.response.AdminFailedCollectionJobPageResponse;
import com.allermeal.api.admin.response.AdminMealItemLabelingPageResponse;
import com.allermeal.api.admin.response.AdminRecollectionResponse;
import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.application.admin.AdminCollectionFailureService;
import com.allermeal.domain.collection.CollectionJobId;
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
public final class AdminCollectionFailureController {

	private final AdminCollectionFailureService collectionFailureService;

	public AdminCollectionFailureController(AdminCollectionFailureService collectionFailureService) {
		this.collectionFailureService = collectionFailureService;
	}

	@GetMapping("/collection-jobs/failed")
	@Operation(deprecated = true)
	public AdminFailedCollectionJobPageResponse findFailedCollectionJobs(
		HttpServletRequest request,
		@RequestParam(name = "page", defaultValue = "1") int page,
		@RequestParam(name = "pageSize", defaultValue = "20") int pageSize
	) {
		return AdminFailedCollectionJobPageResponse.from(collectionFailureService.findFailedCollectionJobs(
			currentUser(request), page, pageSize));
	}

	@GetMapping("/collection-jobs")
	public AdminCollectionJobPageResponse findCollectionJobs(
		HttpServletRequest request,
		@Parameter(schema = @Schema(minimum = "1", defaultValue = "1"))
		@RequestParam(name = "page", defaultValue = "1") int page,
		@Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
		@RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
		@Parameter(schema = @Schema(allowableValues = {"PENDING", "RUNNING", "SUCCEEDED", "FAILED"}))
		@RequestParam(required = false) String status,
		@Parameter(schema = @Schema(format = "uuid")) @RequestParam(required = false) String schoolId,
		@Parameter(schema = @Schema(format = "date")) @RequestParam(required = false) String mealDate,
		@Parameter(schema = @Schema(allowableValues = {"BREAKFAST", "LUNCH", "DINNER"}))
		@RequestParam(required = false) String mealType,
		@Parameter(schema = @Schema(maxLength = 100)) @RequestParam(required = false) String query
	) {
		return AdminCollectionJobPageResponse.from(collectionFailureService.findCollectionJobs(
			currentUser(request), page, pageSize, status, schoolId, mealDate, mealType, query));
	}

	@GetMapping("/meal-item-labelings")
	public AdminMealItemLabelingPageResponse findMealItemLabelings(
		HttpServletRequest request,
		@Parameter(schema = @Schema(minimum = "1", defaultValue = "1"))
		@RequestParam(name = "page", defaultValue = "1") int page,
		@Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
		@RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
		@Parameter(schema = @Schema(allowableValues = {"PENDING", "LABELED", "UNKNOWN", "LABELING_FAILED"}))
		@RequestParam(required = false) String status,
		@Parameter(schema = @Schema(format = "uuid")) @RequestParam(required = false) String schoolId,
		@Parameter(schema = @Schema(format = "date")) @RequestParam(required = false) String mealDate,
		@Parameter(schema = @Schema(allowableValues = {"BREAKFAST", "LUNCH", "DINNER"}))
		@RequestParam(required = false) String mealType,
		@Parameter(schema = @Schema(maxLength = 100)) @RequestParam(required = false) String query
	) {
		return AdminMealItemLabelingPageResponse.from(collectionFailureService.findMealItemLabelings(
			currentUser(request), page, pageSize, status, schoolId, mealDate, mealType, query));
	}

	@GetMapping("/external-api-logs")
	public AdminExternalApiLogPageResponse findExternalApiLogs(
		HttpServletRequest request,
		@Parameter(schema = @Schema(minimum = "1", defaultValue = "1"))
		@RequestParam(name = "page", defaultValue = "1") int page,
		@Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
		@RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
		@Parameter(schema = @Schema(maxLength = 100)) @RequestParam(required = false) String provider,
		@Parameter(schema = @Schema(maxLength = 100)) @RequestParam(required = false) String method,
		@Parameter(schema = @Schema(maxLength = 100)) @RequestParam(required = false) String outcome,
		@Parameter(schema = @Schema(maxLength = 100)) @RequestParam(required = false) String query
	) {
		return AdminExternalApiLogPageResponse.from(collectionFailureService.findExternalApiLogs(
			currentUser(request), page, pageSize, provider, method, outcome, query));
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
