package com.allermeal.infra.admin;

import com.allermeal.application.port.out.AdminUserAccessAuditRepository;
import com.allermeal.application.port.out.command.AdminUserAccessAuditCommand;
import com.allermeal.application.port.out.result.AdminUserAccessAuditPageResult;
import com.allermeal.application.port.out.result.AdminUserAccessAuditResult;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminUserAccessAuditRepository implements AdminUserAccessAuditRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcAdminUserAccessAuditRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void save(AdminUserAccessAuditCommand command) {
		int affectedRows = jdbcTemplate.update("""
			insert into admin_user_access_audit_events (
				event_id, actor_user_id, target_user_id, action, before_role, after_role,
				before_status, after_status, reason, created_at
			)
			values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			command.eventId(), command.actorUserId().value(), command.targetUserId().value(), command.action(),
			command.beforeRole().name(), command.afterRole().name(), command.beforeStatus().name(),
			command.afterStatus().name(), command.reason(), OffsetDateTime.ofInstant(command.createdAt(), java.time.ZoneOffset.UTC));
		if (affectedRows != 1) {
			throw new IllegalStateException("사용자 접근 감사 이력 저장에 실패했습니다.");
		}
	}

	@Override
	public AdminUserAccessAuditPageResult findByTargetUserId(UserId targetUserId, int page, int pageSize) {
		long totalCount = jdbcTemplate.queryForObject("""
			select count(*) from (
				select event_id
				from admin_user_access_audit_events
				where target_user_id = ?
				union all
				select audit_log_id
				from admin_audit_logs
				where target_user_id = ?
					and action = 'PROMOTE_USER_TO_ADMIN'
					and outcome = 'SUCCEEDED'
					and detail is null
			) history
			""", Long.class, targetUserId.value(), targetUserId.value());
		List<AdminUserAccessAuditResult> items = jdbcTemplate.query("""
			select event_id, actor_user_id, action, before_role, after_role, before_status, after_status,
				reason, created_at, source
			from (
				select event_id, actor_user_id, action, before_role, after_role, before_status, after_status,
					reason, created_at, 'USER_ACCESS_AUDIT' as source
				from admin_user_access_audit_events
				where target_user_id = ?
				union all
				select audit_log_id as event_id, actor_user_id, action,
					null::varchar as before_role, null::varchar as after_role,
					null::varchar as before_status, null::varchar as after_status,
					null::varchar as reason, created_at, 'LEGACY_ADMIN_AUDIT' as source
				from admin_audit_logs
				where target_user_id = ?
					and action = 'PROMOTE_USER_TO_ADMIN'
					and outcome = 'SUCCEEDED'
					and detail is null
			) history
			order by created_at desc, event_id desc
			limit ? offset ?
			""", this::mapResult, targetUserId.value(), targetUserId.value(), pageSize,
			(long) (page - 1) * pageSize);
		return new AdminUserAccessAuditPageResult(items, page, pageSize, totalCount);
	}

	private AdminUserAccessAuditResult mapResult(ResultSet resultSet, int rowNumber) throws SQLException {
		UUID actorUserId = resultSet.getObject("actor_user_id", UUID.class);
		OffsetDateTime createdAt = resultSet.getObject("created_at", OffsetDateTime.class);
		return new AdminUserAccessAuditResult(
			resultSet.getObject("event_id", UUID.class),
			actorUserId == null ? null : new UserId(actorUserId),
			resultSet.getString("action"),
			toRole(resultSet.getString("before_role")), toRole(resultSet.getString("after_role")),
			toStatus(resultSet.getString("before_status")), toStatus(resultSet.getString("after_status")),
			resultSet.getString("reason"), createdAt.toInstant(), resultSet.getString("source"));
	}

	private UserRole toRole(String value) {
		return value == null ? null : UserRole.valueOf(value);
	}

	private UserStatus toStatus(String value) {
		return value == null ? null : UserStatus.valueOf(value);
	}
}
