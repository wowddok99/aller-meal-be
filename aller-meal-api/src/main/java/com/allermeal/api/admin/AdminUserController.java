package com.allermeal.api.admin;

import com.allermeal.api.admin.response.AdminUserRoleResponse;
import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.application.admin.AdminUserService;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public final class AdminUserController {

	private final AdminUserService adminUserService;

	public AdminUserController(AdminUserService adminUserService) {
		this.adminUserService = adminUserService;
	}

	@PatchMapping("/{userId}/admin-role")
	public AdminUserRoleResponse promoteToAdmin(HttpServletRequest request, @PathVariable UUID userId) {
		return AdminUserRoleResponse.from(
			adminUserService.promoteToAdmin(currentUser(request), new UserId(userId)));
	}

	private User currentUser(HttpServletRequest request) {
		return (User) Objects.requireNonNull(request.getAttribute(AuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE));
	}
}
