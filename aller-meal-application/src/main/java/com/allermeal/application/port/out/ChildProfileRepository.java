package com.allermeal.application.port.out;

import com.allermeal.domain.child.ChildProfile;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.user.UserId;
import java.util.List;
import java.util.Optional;

public interface ChildProfileRepository {

	ChildProfile save(ChildProfile childProfile);
	List<ChildProfile> findAllByOwnerId(UserId ownerId);
	Optional<ChildProfile> findByIdAndOwnerId(ChildProfileId childProfileId, UserId ownerId);
	boolean deleteByIdAndOwnerId(ChildProfileId childProfileId, UserId ownerId);
}
