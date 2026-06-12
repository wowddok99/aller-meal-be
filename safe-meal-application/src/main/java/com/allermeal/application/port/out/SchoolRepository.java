package com.allermeal.application.port.out;

import com.allermeal.domain.school.School;
import java.util.List;

public interface SchoolRepository {

	List<School> saveAll(List<School> schools);
}
