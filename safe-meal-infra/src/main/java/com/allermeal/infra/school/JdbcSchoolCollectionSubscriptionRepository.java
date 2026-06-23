package com.allermeal.infra.school;

import com.allermeal.application.port.out.SchoolCollectionSubscriptionRepository;
import com.allermeal.application.port.out.command.RegisterSchoolCollectionSubscriptionCommand;
import com.allermeal.application.port.out.command.UnregisterSchoolCollectionSubscriptionCommand;
import com.allermeal.application.port.out.result.SchoolCollectionSubscriptionActivationResult;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSchoolCollectionSubscriptionRepository implements SchoolCollectionSubscriptionRepository {

	private final JdbcClient jdbcClient;

	public JdbcSchoolCollectionSubscriptionRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public SchoolCollectionSubscriptionActivationResult registerChild(RegisterSchoolCollectionSubscriptionCommand command) {
		int registeredChildCount = jdbcClient.sql("""
			INSERT INTO school_collection_subscriptions (
			    school_id, registered_child_count, grace_ends_at, created_at, updated_at
			)
			VALUES (:schoolId, 1, NULL, :changedAt, :changedAt)
			ON CONFLICT (school_id) DO UPDATE
			SET registered_child_count = school_collection_subscriptions.registered_child_count + 1,
			    grace_ends_at = NULL,
			    updated_at = EXCLUDED.updated_at
			RETURNING registered_child_count
			""")
			.param("schoolId", command.schoolId().value())
			.param("changedAt", OffsetDateTime.ofInstant(command.changedAt(), ZoneOffset.UTC))
			.query(Integer.class)
			.single();
		return new SchoolCollectionSubscriptionActivationResult(registeredChildCount == 1);
	}

	@Override
	public void unregisterChild(UnregisterSchoolCollectionSubscriptionCommand command) {
		int updated = jdbcClient.sql("""
			UPDATE school_collection_subscriptions
			SET registered_child_count = registered_child_count - 1,
			    grace_ends_at = CASE WHEN registered_child_count = 1 THEN :graceEndsAt ELSE NULL END,
			    updated_at = :changedAt
			WHERE school_id = :schoolId AND registered_child_count > 0
			""")
			.param("schoolId", command.schoolId().value())
			.param("changedAt", OffsetDateTime.ofInstant(command.changedAt(), ZoneOffset.UTC))
			.param("graceEndsAt", OffsetDateTime.ofInstant(command.graceEndsAt(), ZoneOffset.UTC))
			.update();
		if (updated != 1) {
			throw new IllegalStateException("학교 수집 구독의 등록 자녀 수를 감소할 수 없습니다.");
		}
	}
}
