package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.AdminAuditLogCommand;

public interface AdminAuditLogRepository {

	void save(AdminAuditLogCommand command);
}
