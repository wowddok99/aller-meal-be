package com.allermeal.infra.admin;

import com.allermeal.application.port.out.AdminBootstrapLockRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminBootstrapLockRepository implements AdminBootstrapLockRepository {

	private static final long LOCK_KEY = 0x414C4D41444D494EL;

	private final JdbcTemplate jdbcTemplate;

	public JdbcAdminBootstrapLockRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void acquireTransactionLock() {
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			try (var statement = connection.prepareStatement("select pg_advisory_xact_lock(?)")) {
				statement.setLong(1, LOCK_KEY);
				statement.execute();
			}
			return null;
		});
	}
}
