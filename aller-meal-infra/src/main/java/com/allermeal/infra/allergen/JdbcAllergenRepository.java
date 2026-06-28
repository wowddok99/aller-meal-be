package com.allermeal.infra.allergen;

import com.allermeal.application.port.out.AllergenRepository;
import com.allermeal.domain.allergen.Allergen;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAllergenRepository implements AllergenRepository {

	private final JdbcClient jdbcClient;

	public JdbcAllergenRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public List<Allergen> findAll() {
		return jdbcClient.sql("SELECT allergen_code, name FROM allergens ORDER BY allergen_code")
			.query((resultSet, rowNum) -> new Allergen(
				resultSet.getInt("allergen_code"),
				resultSet.getString("name")))
			.list();
	}
}
