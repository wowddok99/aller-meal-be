package com.allermeal.infra.user;

import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "users")
class UserJpaEntity implements Persistable<UUID> {

	@Id
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "encrypted_email", nullable = false)
	private String encryptedEmail;

	@Column(name = "email_search_hash", nullable = false, unique = true, length = 64)
	private String emailSearchHash;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private UserStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "email_verification_status", nullable = false, length = 20)
	private EmailVerificationStatus emailVerificationStatus;

	@Column(name = "withdrawal_requested_at")
	private Instant withdrawalRequestedAt;

	@Column(name = "withdrawal_due_at")
	private Instant withdrawalDueAt;

	@Column(name = "personal_data_deleted_at")
	private Instant personalDataDeletedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	protected UserJpaEntity() {
	}

	UserJpaEntity(
		UUID id,
		String encryptedEmail,
		String emailSearchHash,
		String passwordHash,
		UserRole role,
		UserStatus status,
		EmailVerificationStatus emailVerificationStatus,
		Instant withdrawalRequestedAt,
		Instant withdrawalDueAt,
		Instant personalDataDeletedAt,
		Instant createdAt,
		Instant updatedAt,
		Long version
	) {
		this.id = id;
		this.encryptedEmail = encryptedEmail;
		this.emailSearchHash = emailSearchHash;
		this.passwordHash = passwordHash;
		this.role = role;
		this.status = status;
		this.emailVerificationStatus = emailVerificationStatus;
		this.withdrawalRequestedAt = withdrawalRequestedAt;
		this.withdrawalDueAt = withdrawalDueAt;
		this.personalDataDeletedAt = personalDataDeletedAt;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.version = version;
	}

	UUID id() {
		return id;
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return version == null;
	}

	String encryptedEmail() {
		return encryptedEmail;
	}

	String emailSearchHash() {
		return emailSearchHash;
	}

	String passwordHash() {
		return passwordHash;
	}

	UserRole role() {
		return role;
	}

	UserStatus status() {
		return status;
	}

	EmailVerificationStatus emailVerificationStatus() {
		return emailVerificationStatus;
	}

	Instant withdrawalRequestedAt() {
		return withdrawalRequestedAt;
	}

	Instant withdrawalDueAt() {
		return withdrawalDueAt;
	}

	Instant personalDataDeletedAt() {
		return personalDataDeletedAt;
	}

	Instant createdAt() {
		return createdAt;
	}

	Instant updatedAt() {
		return updatedAt;
	}

	long version() {
		return version;
	}
}
