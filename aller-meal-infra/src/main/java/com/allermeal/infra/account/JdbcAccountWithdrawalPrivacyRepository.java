package com.allermeal.infra.account;

import com.allermeal.application.port.out.AccountWithdrawalPrivacyRepository;
import com.allermeal.domain.user.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAccountWithdrawalPrivacyRepository implements AccountWithdrawalPrivacyRepository {

	private static final String MASKED_EMAIL = "v1:withdrawal:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA==";
	private static final String MASKED_PASSWORD_HASH = "withdrawn";

	private final JdbcClient jdbcClient;

	public JdbcAccountWithdrawalPrivacyRepository(JdbcClient jdbcClient) {
		this.jdbcClient = Objects.requireNonNull(jdbcClient, "JdbcClient는 null일 수 없습니다.");
	}

	@Override
	public int maskNotificationPersonalData(UserId userId, Instant maskedAt) {
		Objects.requireNonNull(userId, "사용자 ID는 null일 수 없습니다.");
		Objects.requireNonNull(maskedAt, "마스킹 시각은 null일 수 없습니다.");
		return jdbcClient.sql("""
				UPDATE notification_requests
				SET status = 'CANCELED',
				    next_attempt_at = NULL,
				    sent_at = NULL,
				    failure_code = 'PERSONAL_DATA_MASKED',
				    failure_message = NULL,
				    updated_at = :maskedAt
				WHERE user_id = :userId
				  AND status IN ('PENDING', 'SENDING', 'RETRY_PENDING')
				""")
			.param("userId", userId.value())
			.param("maskedAt", Timestamp.from(maskedAt))
			.update();
	}

	@Override
	@Transactional
	public int deleteExpiredPersonalData(Instant dueBeforeInclusive, Instant deletedAt) {
		Objects.requireNonNull(dueBeforeInclusive, "삭제 기준 시각은 null일 수 없습니다.");
		Objects.requireNonNull(deletedAt, "삭제 시각은 null일 수 없습니다.");
		jdbcClient.sql("""
				SELECT user_id
				FROM users
				WHERE status = 'WITHDRAWAL_PENDING'
				  AND withdrawal_due_at <= :dueBeforeInclusive
				  AND personal_data_deleted_at IS NULL
				FOR UPDATE
				""")
			.param("dueBeforeInclusive", Timestamp.from(dueBeforeInclusive))
			.query(UUID.class)
			.list();
		jdbcClient.sql("""
				UPDATE notification_requests
				SET status = 'CANCELED',
				    next_attempt_at = NULL,
				    sent_at = NULL,
				    failure_code = 'PERSONAL_DATA_DELETED',
				    failure_message = NULL,
				    updated_at = :deletedAt
				WHERE user_id IN (
				    SELECT user_id
				    FROM users
				    WHERE status = 'WITHDRAWAL_PENDING'
				      AND withdrawal_due_at <= :dueBeforeInclusive
				      AND personal_data_deleted_at IS NULL
				)
				  AND status IN ('PENDING', 'SENDING', 'RETRY_PENDING')
				""")
			.param("dueBeforeInclusive", Timestamp.from(dueBeforeInclusive))
			.param("deletedAt", Timestamp.from(deletedAt))
			.update();
		jdbcClient.sql("""
				DELETE FROM child_profile_allergens
				WHERE child_id IN (
				    SELECT child.child_id
				    FROM child_profiles child
				    JOIN users account ON account.user_id = child.user_id
				    WHERE account.status = 'WITHDRAWAL_PENDING'
				      AND account.withdrawal_due_at <= :dueBeforeInclusive
				      AND account.personal_data_deleted_at IS NULL
				)
				""")
			.param("dueBeforeInclusive", Timestamp.from(dueBeforeInclusive))
			.update();
		jdbcClient.sql("""
				DELETE FROM notification_preferences
				WHERE child_id IN (
				    SELECT child.child_id
				    FROM child_profiles child
				    JOIN users account ON account.user_id = child.user_id
				    WHERE account.status = 'WITHDRAWAL_PENDING'
				      AND account.withdrawal_due_at <= :dueBeforeInclusive
				      AND account.personal_data_deleted_at IS NULL
				)
				""")
			.param("dueBeforeInclusive", Timestamp.from(dueBeforeInclusive))
			.update();
		jdbcClient.sql("""
				UPDATE child_profiles child
				SET name = '삭제된 자녀',
				    grade = 1,
				    class_number = 1,
				    updated_at = :deletedAt
				FROM users account
				WHERE account.user_id = child.user_id
				  AND account.status = 'WITHDRAWAL_PENDING'
				  AND account.withdrawal_due_at <= :dueBeforeInclusive
				  AND account.personal_data_deleted_at IS NULL
				""")
			.param("dueBeforeInclusive", Timestamp.from(dueBeforeInclusive))
			.param("deletedAt", Timestamp.from(deletedAt))
			.update();
		return jdbcClient.sql("""
				UPDATE users
				SET encrypted_email = :maskedEmail,
				    email_search_hash = replace(user_id::text, '-', '') || replace(user_id::text, '-', ''),
				    password_hash = :maskedPasswordHash,
				    status = 'DISABLED',
				    personal_data_deleted_at = :deletedAt,
				    updated_at = :deletedAt,
				    version = version + 1
				WHERE status = 'WITHDRAWAL_PENDING'
				  AND withdrawal_due_at <= :dueBeforeInclusive
				  AND personal_data_deleted_at IS NULL
				""")
			.param("maskedEmail", MASKED_EMAIL)
			.param("maskedPasswordHash", MASKED_PASSWORD_HASH)
			.param("dueBeforeInclusive", Timestamp.from(dueBeforeInclusive))
			.param("deletedAt", Timestamp.from(deletedAt))
			.update();
	}
}
