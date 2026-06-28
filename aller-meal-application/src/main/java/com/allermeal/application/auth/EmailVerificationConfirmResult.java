package com.allermeal.application.auth;

import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.UserId;

public record EmailVerificationConfirmResult(UserId userId, EmailVerificationStatus emailVerificationStatus) {
}
