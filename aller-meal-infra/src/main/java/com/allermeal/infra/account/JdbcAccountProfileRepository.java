package com.allermeal.infra.account;

import com.allermeal.application.port.out.AccountProfileRepository;
import com.allermeal.application.port.out.result.AccountProfileDataResult;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserStatus;
import com.allermeal.domain.user.UserRole;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccountProfileRepository implements AccountProfileRepository {
	private final JdbcClient jdbcClient;

	public JdbcAccountProfileRepository(JdbcClient jdbcClient) { this.jdbcClient = jdbcClient; }

	@Override
	public Optional<AccountProfileDataResult> findForUpdate(UserId userId) {
		return jdbcClient.sql("""
			SELECT display_name, encrypted_email, status, email_verification_status, session_version, personal_data_deleted_at, withdrawal_due_at, role
			FROM users WHERE user_id = :userId FOR UPDATE
			""")
			.param("userId", userId.value())
			.query((row, index) -> new AccountProfileDataResult(row.getString("display_name"),
				row.getString("encrypted_email"), UserStatus.valueOf(row.getString("status")),
				EmailVerificationStatus.valueOf(row.getString("email_verification_status")),
				row.getLong("session_version"), row.getTimestamp("personal_data_deleted_at") != null,
				row.getTimestamp("withdrawal_due_at") == null ? null : row.getTimestamp("withdrawal_due_at").toInstant(),
				UserRole.valueOf(row.getString("role"))))
			.optional();
	}
}
