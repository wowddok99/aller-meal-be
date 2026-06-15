package com.allermeal.application.port.out;

import com.allermeal.application.meal.PublicMealQueryResult;
import com.allermeal.application.meal.PublicMealTarget;
import com.allermeal.domain.school.SchoolId;
import java.time.LocalDate;
import java.util.Optional;

public interface PublicMealQueryCache {

	Optional<PublicMealQueryResult> find(SchoolId schoolId, LocalDate rangeStart, LocalDate rangeEnd);

	void put(PublicMealQueryResult result);

	boolean tryAcquireDispatch(SchoolId schoolId, PublicMealTarget target);
}
