package com.allermeal.infra.child;

import com.allermeal.application.port.out.ChildProfileRepository;
import com.allermeal.application.child.ChildProfileNotFoundException;
import com.allermeal.domain.child.ChildProfile;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.common.EntityTimestamps;
import com.allermeal.domain.school.SchoolId;
import com.allermeal.domain.user.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcChildProfileRepository implements ChildProfileRepository {

	private final JdbcClient jdbcClient;

	public JdbcChildProfileRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public ChildProfile save(ChildProfile childProfile) {
		return childProfile.isNew() ? insert(childProfile) : update(childProfile);
	}

	@Override
	public List<ChildProfile> findAllByOwnerId(UserId ownerId) {
		return jdbcClient.sql(selectSql() + " WHERE user_id = :userId ORDER BY created_at, child_id")
			.param("userId", ownerId.value()).query(this::map).list();
	}

	@Override
	public Optional<ChildProfile> findByIdAndOwnerId(ChildProfileId childProfileId, UserId ownerId) {
		return jdbcClient.sql(selectSql() + " WHERE child_id = :childId AND user_id = :userId")
			.param("childId", childProfileId.value()).param("userId", ownerId.value()).query(this::map).optional();
	}

	@Override
	public boolean deleteByIdAndOwnerId(ChildProfileId childProfileId, UserId ownerId) {
		return jdbcClient.sql("DELETE FROM child_profiles WHERE child_id = :childId AND user_id = :userId")
			.param("childId", childProfileId.value()).param("userId", ownerId.value()).update() == 1;
	}

	private ChildProfile insert(ChildProfile childProfile) {
		return jdbcClient.sql("""
			INSERT INTO child_profiles (child_id, user_id, name, grade, class_number, school_id, created_at, updated_at)
			VALUES (:childId, :userId, :name, :grade, :classNumber, :schoolId, :createdAt, :updatedAt)
			RETURNING child_id, user_id, name, grade, class_number, school_id, created_at, updated_at, version
			""").param("childId", childProfile.id().value()).param("userId", childProfile.ownerId().value())
			.param("name", childProfile.name()).param("grade", childProfile.grade()).param("classNumber", childProfile.classNumber())
			.param("schoolId", childProfile.schoolId().value()).param("createdAt", java.sql.Timestamp.from(childProfile.timestamps().createdAt()))
			.param("updatedAt", java.sql.Timestamp.from(childProfile.timestamps().updatedAt())).query(this::map).single();
	}

	private ChildProfile update(ChildProfile childProfile) {
		return jdbcClient.sql("""
			UPDATE child_profiles
			SET name = :name, grade = :grade, class_number = :classNumber, school_id = :schoolId,
			    updated_at = :updatedAt, version = version + 1
			WHERE child_id = :childId AND user_id = :userId AND version = :version
			RETURNING child_id, user_id, name, grade, class_number, school_id, created_at, updated_at, version
			""").param("childId", childProfile.id().value()).param("userId", childProfile.ownerId().value())
			.param("name", childProfile.name()).param("grade", childProfile.grade()).param("classNumber", childProfile.classNumber())
			.param("schoolId", childProfile.schoolId().value()).param("updatedAt", java.sql.Timestamp.from(childProfile.timestamps().updatedAt()))
			.param("version", childProfile.version()).query(this::map).optional()
			.orElseThrow(ChildProfileNotFoundException::new);
	}

	private String selectSql() {
		return "SELECT child_id, user_id, name, grade, class_number, school_id, created_at, updated_at, version FROM child_profiles";
	}

	private ChildProfile map(java.sql.ResultSet resultSet, int rowNum) throws java.sql.SQLException {
		return ChildProfile.restoreFromPersistence(new ChildProfileId(resultSet.getObject("child_id", java.util.UUID.class)),
			new UserId(resultSet.getObject("user_id", java.util.UUID.class)), resultSet.getString("name"), resultSet.getInt("grade"),
			resultSet.getInt("class_number"), new SchoolId(resultSet.getObject("school_id", java.util.UUID.class)),
			new EntityTimestamps(resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant()),
			resultSet.getLong("version"));
	}
}
