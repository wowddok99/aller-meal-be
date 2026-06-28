package com.allermeal.infra.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

final class JdbcAdminBootstrapLockRepositoryTest {

	private static final long LOCK_KEY = 0x414C4D41444D494EL;

	@Test
	void executesPostgresqlTransactionAdvisoryLockSql() {
		RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
		JdbcAdminBootstrapLockRepository repository = new JdbcAdminBootstrapLockRepository(jdbcTemplate);

		repository.acquireTransactionLock();

		assertEquals(List.of("select pg_advisory_xact_lock(?)"), jdbcTemplate.sqls);
		assertEquals(List.of(1), jdbcTemplate.longParameterIndexes);
		assertEquals(List.of(LOCK_KEY), jdbcTemplate.longParameters);
		assertTrue(jdbcTemplate.executed);
	}

	private static final class RecordingJdbcTemplate extends JdbcTemplate {

		private final List<String> sqls = new ArrayList<>();
		private final List<Integer> longParameterIndexes = new ArrayList<>();
		private final List<Long> longParameters = new ArrayList<>();
		private boolean executed;

		@Override
		public <T> T execute(ConnectionCallback<T> action) {
			try {
				Connection connection = proxy(Connection.class, (proxy, method, args) -> {
					if (method.getName().equals("prepareStatement")) {
						sqls.add((String) args[0]);
						return preparedStatement();
					}
					return defaultValue(method);
				});
				return action.doInConnection(connection);
			}
			catch (RuntimeException exception) {
				throw exception;
			}
			catch (Exception exception) {
				throw new AssertionError(exception);
			}
		}

		private PreparedStatement preparedStatement() {
			return proxy(PreparedStatement.class, (proxy, method, args) -> {
				if (method.getName().equals("setLong")) {
					longParameterIndexes.add((Integer) args[0]);
					longParameters.add((Long) args[1]);
					return null;
				}
				if (method.getName().equals("execute")) {
					executed = true;
					return true;
				}
				return defaultValue(method);
			});
		}

		private static Object defaultValue(Method method) {
			Class<?> returnType = method.getReturnType();
			if (returnType == boolean.class) {
				return false;
			}
			if (returnType == int.class) {
				return 0;
			}
			if (returnType == long.class) {
				return 0L;
			}
			return null;
		}

		@SuppressWarnings("unchecked")
		private static <T> T proxy(Class<T> type, InvocationHandler handler) {
			return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
		}
	}
}
