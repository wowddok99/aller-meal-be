package com.allermeal.application.port.out;

import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.user.UserId;
import java.util.List;

public interface ChildAllergenRepository {

	boolean replaceAll(UserId ownerId, ChildProfileId childProfileId, List<Integer> allergenCodes);
}
