package com.allermeal.infra.user;

import java.util.UUID;
import java.util.List;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

	Optional<UserJpaEntity> findByEmailSearchHash(String emailSearchHash);

	boolean existsByEmailSearchHash(String emailSearchHash);

	boolean existsByRole(UserRole role);

	@Query("""
		select u.id as userId, u.encryptedEmail as encryptedEmail, u.role as role, u.status as status,
			u.emailVerificationStatus as emailVerificationStatus, u.createdAt as createdAt,
			u.withdrawalDueAt as withdrawalDueAt, u.version as version
		from UserJpaEntity u
		where u.status in :statuses
		""")
	Page<AdminUserQueryProjection> findAdminUsersByStatusIn(
		@Param("statuses") List<UserStatus> statuses,
		Pageable pageable
	);

	@Query("""
		select u.id as userId, u.encryptedEmail as encryptedEmail, u.role as role, u.status as status,
			u.emailVerificationStatus as emailVerificationStatus, u.createdAt as createdAt,
			u.withdrawalDueAt as withdrawalDueAt, u.version as version
		from UserJpaEntity u
		where u.emailSearchHash = :emailSearchHash and u.status in :statuses
		""")
	Page<AdminUserQueryProjection> findAdminUsersByEmailSearchHashAndStatusIn(
		@Param("emailSearchHash") String emailSearchHash,
		@Param("statuses") List<UserStatus> statuses,
		Pageable pageable
	);

	@Query("""
		select u.id as userId, u.encryptedEmail as encryptedEmail, u.role as role, u.status as status,
			u.emailVerificationStatus as emailVerificationStatus, u.createdAt as createdAt,
			u.withdrawalDueAt as withdrawalDueAt, u.version as version
		from UserJpaEntity u
		where u.id = :userId and u.status in :statuses
		""")
	Optional<AdminUserQueryProjection> findAdminUserByIdAndStatusIn(
		@Param("userId") UUID userId,
		@Param("statuses") List<UserStatus> statuses
	);
}
