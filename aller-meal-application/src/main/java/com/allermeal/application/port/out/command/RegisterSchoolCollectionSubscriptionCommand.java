package com.allermeal.application.port.out.command;

import com.allermeal.domain.school.SchoolId;
import java.time.Instant;

public record RegisterSchoolCollectionSubscriptionCommand(SchoolId schoolId, Instant changedAt) {
}
