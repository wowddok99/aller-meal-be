package com.allermeal.api.school.response;

import com.allermeal.domain.school.School;
import java.util.UUID;

public record SchoolResponse(
	UUID id,
	String neisSchoolCode,
	String educationOfficeCode,
	String name,
	String address,
	String region
) {

	public static SchoolResponse from(School school) {
		return new SchoolResponse(
			school.id().value(),
			school.neisSchoolCode(),
			school.educationOfficeCode(),
			school.name(),
			school.address(),
			school.region());
	}
}
