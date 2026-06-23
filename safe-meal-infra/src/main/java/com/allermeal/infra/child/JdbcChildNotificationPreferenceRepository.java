package com.allermeal.infra.child;

import com.allermeal.application.port.out.ChildNotificationPreferenceRepository;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.child.NotificationPreference;
import com.allermeal.domain.common.EntityTimestamps;
import com.allermeal.domain.user.UserId;
import java.time.LocalTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcChildNotificationPreferenceRepository implements ChildNotificationPreferenceRepository {

	private final JdbcClient jdbcClient;

	public JdbcChildNotificationPreferenceRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public Optional<NotificationPreference> findByChildProfileIdAndOwnerId(ChildProfileId childProfileId, UserId ownerId) {
		return jdbcClient.sql(selectSql() + " WHERE preference.child_id = :childId AND child.user_id = :userId")
			.param("childId", childProfileId.value()).param("userId", ownerId.value()).query(this::map).optional();
	}

	@Override
	public Optional<NotificationPreference> upsert(UserId ownerId, NotificationPreference notificationPreference) {
		return jdbcClient.sql("""
			INSERT INTO notification_preferences (child_id, email_enabled, notification_time, timezone, created_at, updated_at)
			SELECT :childId, :emailEnabled, :notificationTime, :timezone, :createdAt, :updatedAt
			FROM child_profiles
			WHERE child_id = :childId AND user_id = :userId
			ON CONFLICT (child_id) DO UPDATE
			SET email_enabled = EXCLUDED.email_enabled,
			    notification_time = EXCLUDED.notification_time,
			    timezone = EXCLUDED.timezone,
			    updated_at = EXCLUDED.updated_at
			RETURNING child_id, email_enabled, notification_time, timezone, created_at, updated_at
			""").param("childId", notificationPreference.childProfileId().value()).param("userId", ownerId.value())
			.param("emailEnabled", notificationPreference.emailEnabled()).param("notificationTime", notificationPreference.notificationTime())
			.param("timezone", notificationPreference.timezone())
			.param("createdAt", java.sql.Timestamp.from(notificationPreference.timestamps().createdAt()))
			.param("updatedAt", java.sql.Timestamp.from(notificationPreference.timestamps().updatedAt())).query(this::map).optional();
	}

	private String selectSql() {
		return "SELECT preference.child_id, preference.email_enabled, preference.notification_time, preference.timezone, "
			+ "preference.created_at, preference.updated_at FROM notification_preferences preference "
			+ "JOIN child_profiles child ON child.child_id = preference.child_id";
	}

	private NotificationPreference map(java.sql.ResultSet resultSet, int rowNum) throws java.sql.SQLException {
		return NotificationPreference.create(new ChildProfileId(resultSet.getObject("child_id", java.util.UUID.class)),
			resultSet.getBoolean("email_enabled"), resultSet.getObject("notification_time", LocalTime.class),
			resultSet.getString("timezone"), new EntityTimestamps(resultSet.getTimestamp("created_at").toInstant(),
				resultSet.getTimestamp("updated_at").toInstant()));
	}
}
