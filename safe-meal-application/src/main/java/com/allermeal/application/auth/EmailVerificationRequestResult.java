package com.allermeal.application.auth;

import com.allermeal.domain.user.EmailVerificationStatus;

public record EmailVerificationRequestResult(EmailVerificationStatus emailVerificationStatus) {
}
