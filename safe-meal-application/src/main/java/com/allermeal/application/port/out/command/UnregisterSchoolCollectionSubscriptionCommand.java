package com.allermeal.application.port.out.command;

import com.allermeal.domain.school.SchoolId;
import java.time.Instant;

public record UnregisterSchoolCollectionSubscriptionCommand(SchoolId schoolId, Instant changedAt, Instant graceEndsAt) {
}
