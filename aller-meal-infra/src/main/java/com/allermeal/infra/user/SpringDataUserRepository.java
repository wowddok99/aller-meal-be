package com.allermeal.infra.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

	Optional<UserJpaEntity> findByEmailSearchHash(String emailSearchHash);

	boolean existsByEmailSearchHash(String emailSearchHash);
}
