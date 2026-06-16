package com.allermeal.application.auth;

import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.UserId;

public record SignupResult(UserId userId, EmailVerificationStatus emailVerificationStatus) {
}
