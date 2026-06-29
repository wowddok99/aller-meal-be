package com.allermeal.infra.admin;

import com.allermeal.application.port.out.AdminAuditLogRepository;
import com.allermeal.application.port.out.command.AdminAuditLogCommand;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminAuditLogRepository implements AdminAuditLogRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcAdminAuditLogRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void save(AdminAuditLogCommand command) {
		Objects.requireNonNull(command, "관리자 감사 로그 command는 null일 수 없습니다.");
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
				insert into admin_audit_logs (
					audit_log_id, actor_user_id, target_user_id, action, outcome, detail, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?)
				""");
			statement.setObject(1, command.auditLogId());
			if (command.actorUserId() == null) {
				statement.setNull(2, Types.OTHER);
			} else {
				statement.setObject(2, command.actorUserId().value());
			}
			statement.setObject(3, command.targetUserId().value());
			statement.setString(4, command.action());
			statement.setString(5, command.outcome());
			statement.setString(6, command.detail());
			statement.setTimestamp(7, Timestamp.from(command.createdAt()));
			return statement;
		});
	}
}
