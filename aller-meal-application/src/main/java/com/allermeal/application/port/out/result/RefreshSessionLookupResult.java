package com.allermeal.application.port.out.result;

import com.allermeal.domain.user.UserId;

public record RefreshSessionLookupResult(UserId userId, long sessionVersion, String familyId) {
}
