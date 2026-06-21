package com.allermeal.domain.child;

import com.allermeal.domain.common.EntityTimestamps;
import com.allermeal.domain.school.SchoolId;
import com.allermeal.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

public final class ChildProfile {

	private final ChildProfileId id;
	private final UserId ownerId;
	private final String name;
	private final int grade;
	private final int classNumber;
	private final SchoolId schoolId;
	private final EntityTimestamps timestamps;
	private final Long version;

	private ChildProfile(ChildProfileId id, UserId ownerId, String name, int grade, int classNumber,
		SchoolId schoolId, EntityTimestamps timestamps, Long version) {
		this.id = Objects.requireNonNull(id, "자녀 ID는 null일 수 없습니다.");
		this.ownerId = Objects.requireNonNull(ownerId, "소유자 ID는 null일 수 없습니다.");
		this.name = requireName(name);
		this.grade = requireGrade(grade);
		this.classNumber = requireClassNumber(classNumber);
		this.schoolId = Objects.requireNonNull(schoolId, "학교 ID는 null일 수 없습니다.");
		this.timestamps = Objects.requireNonNull(timestamps, "자녀 시각 정보는 null일 수 없습니다.");
		if (version != null && version < 0) throw new IllegalArgumentException("영속성 version은 0 이상이어야 합니다.");
		this.version = version;
	}

	public static ChildProfile create(ChildProfileId id, UserId ownerId, String name, int grade, int classNumber,
		SchoolId schoolId, Instant createdAt) {
		return new ChildProfile(id, ownerId, name, grade, classNumber, schoolId, EntityTimestamps.createdAt(createdAt), null);
	}

	public static ChildProfile restoreFromPersistence(ChildProfileId id, UserId ownerId, String name, int grade,
		int classNumber, SchoolId schoolId, EntityTimestamps timestamps, long version) {
		return new ChildProfile(id, ownerId, name, grade, classNumber, schoolId, timestamps, version);
	}

	public ChildProfile update(String name, int grade, int classNumber, SchoolId schoolId, Instant changedAt) {
		if (changedAt == null || changedAt.isBefore(timestamps.updatedAt())) {
			throw new IllegalArgumentException("변경 시각은 기존 updatedAt보다 이전일 수 없습니다.");
		}
		return new ChildProfile(id, ownerId, name, grade, classNumber, schoolId,
			new EntityTimestamps(timestamps.createdAt(), changedAt), version);
	}

	private static String requireName(String value) {
		Objects.requireNonNull(value, "자녀 이름은 null일 수 없습니다.");
		String normalized = value.trim().replaceAll("\\s+", " ");
		if (normalized.isEmpty() || normalized.length() > 100) throw new IllegalArgumentException("자녀 이름은 1자 이상 100자 이하여야 합니다.");
		return normalized;
	}

	private static int requireGrade(int value) {
		if (value < 1 || value > 12) throw new IllegalArgumentException("학년은 1 이상 12 이하여야 합니다.");
		return value;
	}

	private static int requireClassNumber(int value) {
		if (value < 1 || value > 99) throw new IllegalArgumentException("반은 1 이상 99 이하여야 합니다.");
		return value;
	}

	public ChildProfileId id() { return id; }
	public UserId ownerId() { return ownerId; }
	public String name() { return name; }
	public int grade() { return grade; }
	public int classNumber() { return classNumber; }
	public SchoolId schoolId() { return schoolId; }
	public EntityTimestamps timestamps() { return timestamps; }
	public Long version() { return version; }
	public boolean isNew() { return version == null; }
}
