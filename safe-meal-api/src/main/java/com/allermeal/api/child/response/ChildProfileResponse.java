package com.allermeal.api.child.response;

import com.allermeal.domain.child.ChildProfile;
import java.time.Instant;
import java.util.UUID;

public record ChildProfileResponse(UUID id, String name, int grade, int classNumber, UUID schoolId,
	Instant createdAt, Instant updatedAt) {

	public static ChildProfileResponse from(ChildProfile childProfile) {
		return new ChildProfileResponse(childProfile.id().value(), childProfile.name(), childProfile.grade(),
			childProfile.classNumber(), childProfile.schoolId().value(), childProfile.timestamps().createdAt(),
			childProfile.timestamps().updatedAt());
	}
}
