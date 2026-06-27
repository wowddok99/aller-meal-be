package com.allermeal.infra.child;

import com.allermeal.application.port.out.ChildAllergenRepository;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.user.UserId;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcChildAllergenRepository implements ChildAllergenRepository {

	private final JdbcClient jdbcClient;

	public JdbcChildAllergenRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	@Transactional
	public boolean replaceAll(UserId ownerId, ChildProfileId childProfileId, List<Integer> allergenCodes) {
		if (jdbcClient.sql("SELECT child_id FROM child_profiles WHERE child_id = :childId AND user_id = :userId FOR UPDATE")
			.param("childId", childProfileId.value()).param("userId", ownerId.value()).query(java.util.UUID.class).optional().isEmpty()) {
			return false;
		}
		jdbcClient.sql("DELETE FROM child_profile_allergens WHERE child_id = :childId")
			.param("childId", childProfileId.value()).update();
		for (Integer allergenCode : allergenCodes) {
			jdbcClient.sql("""
				INSERT INTO child_profile_allergens (child_id, allergen_code)
				VALUES (:childId, :allergenCode)
				""").param("childId", childProfileId.value()).param("allergenCode", allergenCode).update();
		}
		return true;
	}

	@Override
	public List<Integer> findAllergenCodes(UserId ownerId, ChildProfileId childProfileId) {
		return jdbcClient.sql("""
				SELECT cpa.allergen_code
				FROM child_profile_allergens cpa
				JOIN child_profiles cp ON cp.child_id = cpa.child_id
				WHERE cp.child_id = :childId AND cp.user_id = :userId
				ORDER BY cpa.allergen_code
				""")
			.param("childId", childProfileId.value())
			.param("userId", ownerId.value())
			.query(Integer.class)
			.list();
	}
}
