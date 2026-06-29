package com.allermeal.infra.admin;

import com.allermeal.application.admin.AdminExternalApiLogItemResult;
import com.allermeal.application.admin.AdminExternalApiLogPageResult;
import com.allermeal.application.port.out.ExternalApiLogRepository;
import com.allermeal.application.port.out.command.ExternalApiLogCommand;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExternalApiLogRepository implements ExternalApiLogRepository {

	private final JdbcClient jdbcClient;

	public JdbcExternalApiLogRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public void save(ExternalApiLogCommand command) {
		jdbcClient.sql("""
				INSERT INTO external_api_logs (
				    external_api_log_id, provider, operation, school_id, meal_date, meal_type,
				    method, endpoint, http_status, outcome, failure_code, response_time_millis, created_at
				)
				VALUES (
				    :logId, :provider, :operation, :schoolId, :mealDate, :mealType,
				    :method, :endpoint, :httpStatus, :outcome, :failureCode, :responseTimeMillis, :createdAt
				)
				""")
			.param("logId", command.externalApiLogId())
			.param("provider", command.provider())
			.param("operation", command.operation())
			.param("schoolId", command.schoolId().value())
			.param("mealDate", command.mealDate())
			.param("mealType", command.mealType().name())
			.param("method", command.method())
			.param("endpoint", command.endpoint())
			.param("httpStatus", command.httpStatus())
			.param("outcome", command.outcome())
			.param("failureCode", command.failureCode())
			.param("responseTimeMillis", command.responseTimeMillis())
			.param("createdAt", Timestamp.from(command.createdAt()))
			.update();
	}

	@Override
	public AdminExternalApiLogPageResult findRecent(int page, int pageSize) {
		int offset = Math.multiplyExact(page - 1, pageSize);
		long totalCount = jdbcClient.sql("SELECT count(*) FROM external_api_logs")
			.query(Long.class)
			.single();
		var items = jdbcClient.sql("""
				SELECT external_api_log_id, provider, operation, school_id, meal_date, meal_type,
				       method, endpoint, http_status, outcome, failure_code, response_time_millis, created_at
				FROM external_api_logs
				ORDER BY created_at DESC, external_api_log_id DESC
				LIMIT :limit OFFSET :offset
				""")
			.param("limit", pageSize)
			.param("offset", offset)
			.query(this::mapItem)
			.list();
		return new AdminExternalApiLogPageResult(items, page, pageSize, Math.toIntExact(totalCount));
	}

	private AdminExternalApiLogItemResult mapItem(ResultSet resultSet, int rowNum) throws SQLException {
		return new AdminExternalApiLogItemResult(
			resultSet.getObject("external_api_log_id", UUID.class),
			resultSet.getString("provider"),
			resultSet.getString("operation"),
			new SchoolId(resultSet.getObject("school_id", UUID.class)),
			resultSet.getObject("meal_date", java.time.LocalDate.class),
			MealType.valueOf(resultSet.getString("meal_type")),
			resultSet.getString("method"),
			resultSet.getString("endpoint"),
			resultSet.getObject("http_status", Integer.class),
			resultSet.getString("outcome"),
			resultSet.getString("failure_code"),
			resultSet.getObject("response_time_millis", Long.class),
			resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
	}
}
