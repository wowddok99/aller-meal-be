package com.allermeal.application.school;

import com.allermeal.application.port.out.NeisSchoolClient;
import com.allermeal.application.port.out.SchoolRepository;
import com.allermeal.application.port.out.result.SchoolSearchResult;
import com.allermeal.domain.school.School;
import com.allermeal.domain.school.SchoolId;

public final class SchoolSearchService {

	private final NeisSchoolClient neisSchoolClient;
	private final SchoolRepository schoolRepository;

	public SchoolSearchService(NeisSchoolClient neisSchoolClient, SchoolRepository schoolRepository) {
		this.neisSchoolClient = neisSchoolClient;
		this.schoolRepository = schoolRepository;
	}

	public SchoolSearchResult search(String keyword, int page, int pageSize) {
		if (keyword == null || keyword.isBlank()) {
			throw new InvalidSchoolSearchRequestException("학교 검색어는 필수입니다.");
		}
		if (page < 1 || pageSize < 1 || pageSize > 100) {
			throw new InvalidSchoolSearchRequestException("페이지는 1 이상, pageSize는 1 이상 100 이하여야 합니다.");
		}
		SchoolSearchResult result = neisSchoolClient.search(keyword.trim(), page, pageSize);
		return new SchoolSearchResult(schoolRepository.saveAll(result.schools()), result.totalCount());
	}

	public School findById(SchoolId schoolId) {
		return schoolRepository.findById(schoolId).orElseThrow(SchoolNotFoundException::new);
	}
}
