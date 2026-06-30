package com.allermeal.application.port.out;

import com.allermeal.application.admin.AdminDashboardSummaryResult;
import java.time.Instant;

public interface AdminDashboardSummaryRepository {

	AdminDashboardSummaryResult findOrCreate(Instant generatedAfter, Instant now);
}
