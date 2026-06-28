package com.allermeal.application.port.out;

import com.allermeal.domain.school.School;
import com.allermeal.domain.school.SchoolId;
import java.util.List;
import java.util.Optional;

public interface SchoolRepository {

	List<School> saveAll(List<School> schools);

	Optional<School> findById(SchoolId schoolId);
}
