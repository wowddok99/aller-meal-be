package com.allermeal.api.account;

import com.allermeal.api.account.response.AccountProfileResponse;
import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.application.account.AccountProfileResult;
import com.allermeal.application.account.AccountProfileService;
import com.allermeal.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountProfileController {
	private final AccountProfileService service;

	public AccountProfileController(AccountProfileService service) { this.service = service; }

	@GetMapping("/api/v1/account/profile")
	public ResponseEntity<AccountProfileResponse> findProfile(HttpServletRequest request) {
		User user = (User) Objects.requireNonNull(request.getAttribute(AuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE));
		AccountProfileResult profile = service.findProfile(user.id(), user.sessionVersion());
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(new AccountProfileResponse(profile.displayName(), profile.email(), profile.userStatus(), profile.role()));
	}
}
