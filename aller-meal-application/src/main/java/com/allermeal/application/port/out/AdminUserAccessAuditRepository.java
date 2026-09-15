package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.AdminUserAccessAuditCommand;
import com.allermeal.application.port.out.result.AdminUserAccessAuditPageResult;
import com.allermeal.domain.user.UserId;

public interface AdminUserAccessAuditRepository {

	void save(AdminUserAccessAuditCommand command);

	AdminUserAccessAuditPageResult findByTargetUserId(UserId targetUserId, int page, int pageSize);
}
