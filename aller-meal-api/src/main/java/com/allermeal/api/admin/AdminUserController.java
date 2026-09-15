package com.allermeal.api.admin;

import com.allermeal.api.admin.response.AdminUserRoleResponse;
import com.allermeal.api.admin.request.AdminUserRoleChangeRequest;
import com.allermeal.api.admin.request.AdminUserSuspensionRequest;
import com.allermeal.api.admin.response.AdminUserAccessHistoryPageResponse;
import com.allermeal.api.admin.response.AdminUserDetailResponse;
import com.allermeal.api.admin.response.AdminUserPageResponse;
import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.application.admin.AdminUserService;
import com.allermeal.application.admin.AdminUserRoleChangeCommand;
import com.allermeal.application.admin.AdminUserSuspensionCommand;
import com.allermeal.application.admin.AdminInvalidUserChangeRequestException;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public final class AdminUserController {

	private final AdminUserService adminUserService;

	public AdminUserController(AdminUserService adminUserService) {
		this.adminUserService = adminUserService;
	}

	@GetMapping
	public AdminUserPageResponse findUsers(
		HttpServletRequest request,
		@RequestParam(name = "query", required = false) String query,
		@Parameter(schema = @Schema(allowableValues = {"ACTIVE", "WITHDRAWAL_PENDING", "SUSPENDED"}))
		@RequestParam(name = "status", required = false) String status,
		@Parameter(schema = @Schema(minimum = "1", defaultValue = "1"))
		@RequestParam(name = "page", defaultValue = "1") int page,
		@Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
		@RequestParam(name = "pageSize", defaultValue = "20") int pageSize
	) {
		return AdminUserPageResponse.from(adminUserService.findUsers(
			currentUser(request), query, status, page, pageSize));
	}

	@GetMapping("/{userId}")
	public AdminUserDetailResponse findUser(HttpServletRequest request, @PathVariable UUID userId) {
		return AdminUserDetailResponse.from(adminUserService.findUser(currentUser(request), new UserId(userId)));
	}

	@GetMapping("/{userId}/access-history")
	public AdminUserAccessHistoryPageResponse findAccessHistory(
		HttpServletRequest request,
		@PathVariable UUID userId,
		@Parameter(schema = @Schema(minimum = "1", defaultValue = "1"))
		@RequestParam(name = "page", defaultValue = "1") int page,
		@Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
		@RequestParam(name = "pageSize", defaultValue = "20") int pageSize
	) {
		return AdminUserAccessHistoryPageResponse.from(adminUserService.findAccessHistory(
			currentUser(request), new UserId(userId), page, pageSize));
	}

	@PatchMapping("/{userId}/admin-role")
	public AdminUserRoleResponse promoteToAdmin(
		HttpServletRequest request,
		@PathVariable UUID userId,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(required = true)
		@Valid @RequestBody(required = false) AdminUserRoleChangeRequest body
	) {
		if (body == null) throw new AdminInvalidUserChangeRequestException();
		return AdminUserRoleResponse.from(
			adminUserService.promoteToAdmin(
				currentUser(request), new UserId(userId), new AdminUserRoleChangeCommand(body.reason(), body.expectedVersion())));
	}

	@PatchMapping("/{userId}/suspension")
	public AdminUserRoleResponse changeSuspension(
		HttpServletRequest request,
		@PathVariable UUID userId,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(required = true)
		@Valid @RequestBody(required = false) AdminUserSuspensionRequest body
	) {
		if (body == null) throw new AdminInvalidUserChangeRequestException();
		return AdminUserRoleResponse.from(adminUserService.changeSuspension(
			currentUser(request), new UserId(userId),
			new AdminUserSuspensionCommand(body.action(), body.reason(), body.expectedVersion())));
	}

	private User currentUser(HttpServletRequest request) {
		return (User) Objects.requireNonNull(request.getAttribute(AuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE));
	}
}
