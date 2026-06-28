package com.allermeal.infra.notification;

import com.allermeal.application.port.out.NotificationTargetRepository;
import com.allermeal.application.port.out.command.NotificationTargetCommand;
import com.allermeal.application.port.out.result.DueNotificationPreferenceResult;
import com.allermeal.application.port.out.result.NotificationTargetSaveResult;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.school.SchoolId;
import com.allermeal.domain.user.UserId;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcNotificationTargetRepository implements NotificationTargetRepository {

	private final JdbcClient jdbcClient;

	public JdbcNotificationTargetRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public List<DueNotificationPreferenceResult> findDuePreferences(
		LocalDate notificationDate,
		LocalTime windowStartInclusive,
		LocalTime windowEndInclusive
	) {
		return jdbcClient.sql("""
				SELECT preference.child_id, child.user_id, child.school_id, preference.notification_time
				FROM notification_preferences preference
				JOIN child_profiles child ON child.child_id = preference.child_id
				WHERE preference.email_enabled = TRUE
				  AND preference.timezone = 'Asia/Seoul'
				  AND preference.notification_time >= :windowStart
				  AND preference.notification_time <= :windowEnd
				  AND NOT EXISTS (
				      SELECT 1
				      FROM notification_targets target
				      WHERE target.child_id = preference.child_id
				        AND target.notification_date = :notificationDate
				  )
				ORDER BY preference.notification_time, preference.child_id
				""")
			.param("notificationDate", notificationDate)
			.param("windowStart", windowStartInclusive)
			.param("windowEnd", windowEndInclusive)
			.query((resultSet, rowNum) -> new DueNotificationPreferenceResult(
				new ChildProfileId(resultSet.getObject("child_id", UUID.class)),
				new UserId(resultSet.getObject("user_id", UUID.class)),
				new SchoolId(resultSet.getObject("school_id", UUID.class)),
				resultSet.getObject("notification_time", LocalTime.class)))
			.list();
	}

	@Override
	@Transactional
	public NotificationTargetSaveResult saveAll(List<NotificationTargetCommand> commands) {
		int created = 0;
		for (NotificationTargetCommand command : commands) {
			created += jdbcClient.sql("""
					INSERT INTO notification_targets (
					    notification_target_id, child_id, user_id, school_id, notification_date,
					    notification_time, timezone, reason, risk_level, risk_version, meal_count,
					    created_at, updated_at
					)
					VALUES (
					    :targetId, :childId, :userId, :schoolId, :notificationDate,
					    :notificationTime, :timezone, :reason, :riskLevel, :riskVersion, :mealCount,
					    :createdAt, :createdAt
					)
					ON CONFLICT (child_id, notification_date) DO NOTHING
					""")
				.param("targetId", command.notificationTargetId())
				.param("childId", command.childProfileId().value())
				.param("userId", command.ownerId().value())
				.param("schoolId", command.schoolId().value())
				.param("notificationDate", command.notificationDate())
				.param("notificationTime", command.notificationTime())
				.param("timezone", command.timezone())
				.param("reason", command.reason().name())
				.param("riskLevel", command.riskLevel() == null ? null : command.riskLevel().name())
				.param("riskVersion", command.riskVersion())
				.param("mealCount", command.mealCount())
				.param("createdAt", Timestamp.from(command.createdAt()))
				.update();
		}
		return new NotificationTargetSaveResult(created, commands.size() - created);
	}
}
