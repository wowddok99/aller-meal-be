package com.allermeal.domain.user;

import com.allermeal.domain.common.EntityTimestamps;
import java.time.Instant;
import java.util.Objects;

public final class User {

	private final UserId id;
	private final EncryptedEmail encryptedEmail;
	private final EmailSearchHash emailSearchHash;
	private final PasswordHash passwordHash;
	private final UserRole role;
	private final UserStatus status;
	private final EmailVerificationStatus emailVerificationStatus;
	private final EntityTimestamps timestamps;
	private final Long version;

	private User(
		UserId id,
		EncryptedEmail encryptedEmail,
		EmailSearchHash emailSearchHash,
		PasswordHash passwordHash,
		UserRole role,
		UserStatus status,
		EmailVerificationStatus emailVerificationStatus,
		EntityTimestamps timestamps,
		Long version
	) {
		this.id = Objects.requireNonNull(id, "사용자 ID는 null일 수 없습니다.");
		this.encryptedEmail = Objects.requireNonNull(encryptedEmail, "암호화 이메일은 null일 수 없습니다.");
		this.emailSearchHash = Objects.requireNonNull(emailSearchHash, "이메일 검색 해시는 null일 수 없습니다.");
		this.passwordHash = Objects.requireNonNull(passwordHash, "비밀번호 해시는 null일 수 없습니다.");
		this.role = Objects.requireNonNull(role, "사용자 역할은 null일 수 없습니다.");
		this.status = Objects.requireNonNull(status, "사용자 상태는 null일 수 없습니다.");
		this.emailVerificationStatus = Objects.requireNonNull(
			emailVerificationStatus, "이메일 인증 상태는 null일 수 없습니다.");
		this.timestamps = Objects.requireNonNull(timestamps, "사용자 시각 정보는 null일 수 없습니다.");
		if (version != null && version < 0) {
			throw new IllegalArgumentException("영속성 version은 0 이상이어야 합니다.");
		}
		this.version = version;
	}

	public static User create(
		UserId id,
		EncryptedEmail encryptedEmail,
		EmailSearchHash emailSearchHash,
		PasswordHash passwordHash,
		Instant createdAt
	) {
		return new User(
			id,
			encryptedEmail,
			emailSearchHash,
			passwordHash,
			UserRole.MEMBER,
			UserStatus.ACTIVE,
			EmailVerificationStatus.UNVERIFIED,
			EntityTimestamps.createdAt(createdAt),
			null);
	}

	public static User createAdmin(
		UserId id,
		EncryptedEmail encryptedEmail,
		EmailSearchHash emailSearchHash,
		PasswordHash passwordHash,
		Instant createdAt
	) {
		return new User(
			id,
			encryptedEmail,
			emailSearchHash,
			passwordHash,
			UserRole.ADMIN,
			UserStatus.ACTIVE,
			EmailVerificationStatus.VERIFIED,
			EntityTimestamps.createdAt(createdAt),
			null);
	}

	public static User restoreFromPersistence(
		UserId id,
		EncryptedEmail encryptedEmail,
		EmailSearchHash emailSearchHash,
		PasswordHash passwordHash,
		UserRole role,
		UserStatus status,
		EmailVerificationStatus emailVerificationStatus,
		EntityTimestamps timestamps,
		long version
	) {
		return new User(
			id,
			encryptedEmail,
			emailSearchHash,
			passwordHash,
			role,
			status,
			emailVerificationStatus,
			timestamps,
			version);
	}

	public User verifyEmail(Instant changedAt) {
		if (emailVerificationStatus == EmailVerificationStatus.VERIFIED) {
			return this;
		}
		if (status == UserStatus.DISABLED) {
			throw new IllegalStateException("DISABLED 사용자는 상태를 변경할 수 없습니다.");
		}
		return withState(status, EmailVerificationStatus.VERIFIED, changedAt);
	}

	public User promoteToAdmin(Instant changedAt) {
		if (role == UserRole.ADMIN) {
			return this;
		}
		if (status != UserStatus.ACTIVE) {
			throw new IllegalStateException("ACTIVE 사용자만 관리자 권한으로 승격할 수 있습니다.");
		}
		Objects.requireNonNull(changedAt, "변경 시각은 null일 수 없습니다.");
		if (changedAt.isBefore(timestamps.updatedAt())) {
			throw new IllegalArgumentException("변경 시각은 기존 updatedAt보다 이전일 수 없습니다.");
		}
		return new User(
			id,
			encryptedEmail,
			emailSearchHash,
			passwordHash,
			UserRole.ADMIN,
			status,
			emailVerificationStatus,
			new EntityTimestamps(timestamps.createdAt(), changedAt),
			version);
	}

	public User changePassword(PasswordHash nextPasswordHash, Instant changedAt) {
		Objects.requireNonNull(nextPasswordHash, "비밀번호 해시는 null일 수 없습니다.");
		if (status != UserStatus.ACTIVE || emailVerificationStatus != EmailVerificationStatus.VERIFIED) {
			throw new IllegalStateException("활성화되고 인증된 사용자만 비밀번호를 변경할 수 있습니다.");
		}
		if (changedAt == null || changedAt.isBefore(timestamps.updatedAt())) {
			throw new IllegalArgumentException("변경 시각은 기존 updatedAt보다 이전일 수 없습니다.");
		}
		return new User(
			id,
			encryptedEmail,
			emailSearchHash,
			nextPasswordHash,
			role,
			status,
			emailVerificationStatus,
			new EntityTimestamps(timestamps.createdAt(), changedAt),
			version);
	}

	public User requestWithdrawal(Instant changedAt) {
		requireStatus(UserStatus.ACTIVE, "ACTIVE 사용자만 탈퇴를 요청할 수 있습니다.");
		return withState(UserStatus.WITHDRAWAL_PENDING, emailVerificationStatus, changedAt);
	}

	public User cancelWithdrawal(Instant changedAt) {
		requireStatus(UserStatus.WITHDRAWAL_PENDING, "WITHDRAWAL_PENDING 사용자만 탈퇴를 취소할 수 있습니다.");
		return withState(UserStatus.ACTIVE, emailVerificationStatus, changedAt);
	}

	public User disable(Instant changedAt) {
		if (status == UserStatus.DISABLED) {
			throw new IllegalStateException("DISABLED 사용자는 상태를 변경할 수 없습니다.");
		}
		return withState(UserStatus.DISABLED, emailVerificationStatus, changedAt);
	}

	private User withState(
		UserStatus nextStatus,
		EmailVerificationStatus nextEmailVerificationStatus,
		Instant changedAt
	) {
		Objects.requireNonNull(changedAt, "변경 시각은 null일 수 없습니다.");
		if (changedAt.isBefore(timestamps.updatedAt())) {
			throw new IllegalArgumentException("변경 시각은 기존 updatedAt보다 이전일 수 없습니다.");
		}
		return new User(
			id,
			encryptedEmail,
			emailSearchHash,
			passwordHash,
			role,
			nextStatus,
			nextEmailVerificationStatus,
			new EntityTimestamps(timestamps.createdAt(), changedAt),
			version);
	}

	private void requireStatus(UserStatus requiredStatus, String message) {
		if (status != requiredStatus) {
			throw new IllegalStateException(message);
		}
	}

	public UserId id() {
		return id;
	}

	public EncryptedEmail encryptedEmail() {
		return encryptedEmail;
	}

	public EmailSearchHash emailSearchHash() {
		return emailSearchHash;
	}

	public PasswordHash passwordHash() {
		return passwordHash;
	}

	public UserRole role() {
		return role;
	}

	public UserStatus status() {
		return status;
	}

	public EmailVerificationStatus emailVerificationStatus() {
		return emailVerificationStatus;
	}

	public EntityTimestamps timestamps() {
		return timestamps;
	}

	public Long version() {
		return version;
	}

	public boolean isNew() {
		return version == null;
	}

	@Override
	public String toString() {
		return "User[id=" + id + ", role=" + role + ", status=" + status
			+ ", emailVerificationStatus=" + emailVerificationStatus + ", timestamps=" + timestamps
			+ ", version=" + version + "]";
	}
}
