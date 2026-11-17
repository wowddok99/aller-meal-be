package com.allermeal.infra.notification;

import com.allermeal.application.port.out.NotificationMailContextRepository;
import com.allermeal.application.port.out.result.NotificationMailContextResult;
import com.allermeal.application.port.out.result.PendingNotificationTargetResult;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.notification.NotificationReason;
import com.allermeal.domain.notification.NotificationRequest;
import com.allermeal.domain.risk.MealRiskLevel;
import com.allermeal.domain.school.SchoolId;
import com.allermeal.domain.user.UserId;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcNotificationMailContextRepository implements NotificationMailContextRepository {

	private final JdbcClient jdbc;

	public JdbcNotificationMailContextRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<NotificationMailContextResult> findForDelivery(NotificationRequest request) {
		// PostgreSQL JOIN 실행계획에 잠금 순서를 맡기지 않는다. request 행 잠금은 이 조회 뒤 획득한다.
		String ownerStatus = jdbc.sql("SELECT status FROM users WHERE user_id = :ownerId FOR UPDATE")
			.param("ownerId", request.ownerId().value()).query(String.class).optional().orElse(null);
		if (!"ACTIVE".equals(ownerStatus)) return Optional.empty();
		ChildSnapshot child = jdbc.sql("""
			SELECT school_id, name FROM child_profiles
			WHERE child_id = :childId AND user_id = :ownerId FOR SHARE
			""").param("childId", request.childProfileId().value()).param("ownerId", request.ownerId().value())
			.query((rs, row) -> new ChildSnapshot(new SchoolId(rs.getObject("school_id", UUID.class)), rs.getString("name")))
			.optional().orElse(null);
		if (child == null) return Optional.empty();
		PendingNotificationTargetResult target = jdbc.sql("""
			SELECT notification_target_id, child_id, user_id, notification_date, reason, risk_level, risk_version, meal_count
			FROM notification_targets
			WHERE notification_target_id = :targetId AND child_id = :childId AND user_id = :ownerId
			  AND notification_date = :notificationDate AND school_id = :schoolId
			FOR SHARE
			""")
			.param("targetId", request.notificationTargetId()).param("childId", request.childProfileId().value())
			.param("ownerId", request.ownerId().value()).param("notificationDate", request.notificationDate())
			.param("schoolId", child.schoolId().value())
			.query((rs, row) -> new PendingNotificationTargetResult(
				rs.getObject("notification_target_id", UUID.class), new ChildProfileId(rs.getObject("child_id", UUID.class)),
				new UserId(rs.getObject("user_id", UUID.class)), rs.getObject("notification_date", LocalDate.class),
				NotificationReason.valueOf(rs.getString("reason")),
				rs.getString("risk_level") == null ? null : MealRiskLevel.valueOf(rs.getString("risk_level")),
				rs.getString("risk_version"), rs.getInt("meal_count")))
			.optional().orElse(null);
		if (target == null) return Optional.empty();
		return jdbc.sql("SELECT name FROM schools WHERE school_id = :schoolId FOR SHARE")
			.param("schoolId", child.schoolId().value()).query(String.class).optional()
			.map(schoolName -> new NotificationMailContextResult(target, child.schoolId(), child.name(), schoolName));
	}

	private record ChildSnapshot(SchoolId schoolId, String name) { }
}
