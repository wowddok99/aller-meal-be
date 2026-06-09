package com.allermeal.domain.school;

import java.util.Objects;

public record School(
	SchoolId id,
	String neisSchoolCode,
	String educationOfficeCode,
	String name,
	String address,
	String region
) {

	public School {
		Objects.requireNonNull(id, "학교 ID는 null일 수 없습니다.");
		neisSchoolCode = requireText(neisSchoolCode, "NEIS 학교 코드는 필수입니다.");
		educationOfficeCode = requireText(educationOfficeCode, "교육청 코드는 필수입니다.");
		name = requireText(name, "학교 이름은 필수입니다.");
		address = requireText(address, "학교 주소는 필수입니다.");
		region = requireText(region, "학교 지역은 필수입니다.");
	}

	private static String requireText(String value, String message) {
		Objects.requireNonNull(value, message);
		String normalized = value.trim().replaceAll("\\s+", " ");
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(message);
		}
		return normalized;
	}
}
