package com.allermeal.application.port.out;

import com.allermeal.application.admin.AdminExternalApiLogPageResult;
import com.allermeal.application.port.out.command.ExternalApiLogCommand;

public interface ExternalApiLogRepository {

	void save(ExternalApiLogCommand command);

	AdminExternalApiLogPageResult findRecent(int page, int pageSize);
}
