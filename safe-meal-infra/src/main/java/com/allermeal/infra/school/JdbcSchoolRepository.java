package com.allermeal.infra.school;

import com.allermeal.application.port.out.SchoolRepository;
import com.allermeal.domain.school.School;
import com.allermeal.domain.school.SchoolId;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSchoolRepository implements SchoolRepository {

	private static final String UPSERT_SQL = """
		INSERT INTO schools (
		    school_id, neis_school_code, education_office_code, name, address, region, created_at, updated_at
		)
		VALUES (:schoolId, :neisSchoolCode, :educationOfficeCode, :name, :address, :region, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
		ON CONFLICT (neis_school_code) DO UPDATE SET
		    education_office_code = EXCLUDED.education_office_code,
		    name = EXCLUDED.name,
		    address = EXCLUDED.address,
		    region = EXCLUDED.region,
		    updated_at = CURRENT_TIMESTAMP
		RETURNING school_id, neis_school_code, education_office_code, name, address, region
		""";

	private final JdbcClient jdbcClient;

	public JdbcSchoolRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public List<School> saveAll(List<School> schools) {
		return schools.stream().map(this::save).toList();
	}

	private School save(School school) {
		return jdbcClient.sql(UPSERT_SQL)
			.param("schoolId", school.id().value())
			.param("neisSchoolCode", school.neisSchoolCode())
			.param("educationOfficeCode", school.educationOfficeCode())
			.param("name", school.name())
			.param("address", school.address())
			.param("region", school.region())
			.query((resultSet, rowNum) -> new School(
				new SchoolId(resultSet.getObject("school_id", java.util.UUID.class)),
				resultSet.getString("neis_school_code"),
				resultSet.getString("education_office_code"),
				resultSet.getString("name"),
				resultSet.getString("address"),
				resultSet.getString("region")))
			.single();
	}
}
