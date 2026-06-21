package com.allermeal.application.child;

import com.allermeal.application.port.out.ChildProfileRepository;
import com.allermeal.application.port.out.SchoolRepository;
import com.allermeal.application.school.SchoolNotFoundException;
import com.allermeal.domain.child.ChildProfile;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.school.SchoolId;
import com.allermeal.domain.user.UserId;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public final class ChildProfileService {

	private final ChildProfileRepository childProfileRepository;
	private final SchoolRepository schoolRepository;
	private final Clock clock;

	public ChildProfileService(ChildProfileRepository childProfileRepository, SchoolRepository schoolRepository, Clock clock) {
		this.childProfileRepository = childProfileRepository;
		this.schoolRepository = schoolRepository;
		this.clock = clock;
	}

	public ChildProfile create(UserId ownerId, CreateChildProfileCommand command) {
		requireSchool(command.schoolId());
		try {
			return childProfileRepository.save(ChildProfile.create(new ChildProfileId(UUID.randomUUID()), ownerId,
				command.name(), command.grade(), command.classNumber(), command.schoolId(), clock.instant()));
		} catch (IllegalArgumentException exception) {
			throw new InvalidChildProfileRequestException();
		}
	}

	public List<ChildProfile> findAll(UserId ownerId) { return childProfileRepository.findAllByOwnerId(ownerId); }

	public ChildProfile find(UserId ownerId, ChildProfileId childProfileId) {
		return childProfileRepository.findByIdAndOwnerId(childProfileId, ownerId).orElseThrow(ChildProfileNotFoundException::new);
	}

	public ChildProfile update(UserId ownerId, ChildProfileId childProfileId, UpdateChildProfileCommand command) {
		ChildProfile childProfile = find(ownerId, childProfileId);
		requireSchool(command.schoolId());
		try {
			return childProfileRepository.save(childProfile.update(command.name(), command.grade(), command.classNumber(), command.schoolId(), clock.instant()));
		} catch (IllegalArgumentException exception) {
			throw new InvalidChildProfileRequestException();
		}
	}

	public void delete(UserId ownerId, ChildProfileId childProfileId) {
		if (!childProfileRepository.deleteByIdAndOwnerId(childProfileId, ownerId)) throw new ChildProfileNotFoundException();
	}

	private void requireSchool(SchoolId schoolId) {
		if (schoolRepository.findById(schoolId).isEmpty()) throw new SchoolNotFoundException();
	}
}
