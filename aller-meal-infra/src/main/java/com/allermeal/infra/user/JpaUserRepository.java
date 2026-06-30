package com.allermeal.infra.user;

import com.allermeal.application.auth.DuplicateEmailException;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.domain.common.EntityTimestamps;
import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.EncryptedEmail;
import com.allermeal.domain.user.PasswordHash;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class JpaUserRepository implements UserRepository {

	private final SpringDataUserRepository repository;

	public JpaUserRepository(SpringDataUserRepository repository) {
		this.repository = repository;
	}

	@Override
	public User save(User user) {
		try {
			return toDomain(repository.saveAndFlush(toEntity(user)));
		} catch (DataIntegrityViolationException exception) {
			throw new DuplicateEmailException();
		}
	}

	@Override
	public Optional<User> findById(UserId userId) {
		return repository.findById(userId.value()).map(this::toDomain);
	}

	@Override
	public Optional<User> findByEmailSearchHash(EmailSearchHash emailSearchHash) {
		return repository.findByEmailSearchHash(emailSearchHash.value()).map(this::toDomain);
	}

	@Override
	public boolean existsByEmailSearchHash(EmailSearchHash emailSearchHash) {
		return repository.existsByEmailSearchHash(emailSearchHash.value());
	}

	@Override
	public boolean existsByRole(UserRole role) {
		return repository.existsByRole(role);
	}

	private UserJpaEntity toEntity(User user) {
		return new UserJpaEntity(
			user.id().value(),
			user.encryptedEmail().value(),
			user.emailSearchHash().value(),
			user.passwordHash().value(),
			user.role(),
			user.status(),
			user.emailVerificationStatus(),
			user.withdrawalRequestedAt(),
			user.withdrawalDueAt(),
			user.personalDataDeletedAt(),
			user.timestamps().createdAt(),
			user.timestamps().updatedAt(),
			user.version());
	}

	private User toDomain(UserJpaEntity entity) {
		return User.restoreFromPersistence(
			new UserId(entity.id()),
			new EncryptedEmail(entity.encryptedEmail()),
			new EmailSearchHash(entity.emailSearchHash()),
			new PasswordHash(entity.passwordHash()),
			entity.role(),
			entity.status(),
			entity.emailVerificationStatus(),
			entity.withdrawalRequestedAt(),
			entity.withdrawalDueAt(),
			entity.personalDataDeletedAt(),
			new EntityTimestamps(entity.createdAt(), entity.updatedAt()),
			entity.version());
	}
}
