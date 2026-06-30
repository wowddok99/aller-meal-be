package com.allermeal.api.account;

import com.allermeal.api.account.response.AccountWithdrawalResponse;
import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.application.account.AccountWithdrawalService;
import com.allermeal.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account/withdrawal")
public class AccountWithdrawalController {

	private final AccountWithdrawalService withdrawalService;

	public AccountWithdrawalController(AccountWithdrawalService withdrawalService) {
		this.withdrawalService = Objects.requireNonNull(
			withdrawalService, "AccountWithdrawalService는 null일 수 없습니다.");
	}

	@PostMapping
	public AccountWithdrawalResponse requestWithdrawal(HttpServletRequest request) {
		return AccountWithdrawalResponse.from(withdrawalService.requestWithdrawal(currentUser(request).id()));
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancelWithdrawal(HttpServletRequest request) {
		withdrawalService.cancelWithdrawal(currentUser(request).id());
	}

	private User currentUser(HttpServletRequest request) {
		return (User) Objects.requireNonNull(request.getAttribute(AuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE));
	}
}
