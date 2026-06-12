package com.allermeal.application.port.out;

import com.allermeal.domain.school.School;
import java.util.List;

public interface NeisSchoolClient {

	SchoolSearchResult search(String keyword, int page, int pageSize);

	record SchoolSearchResult(List<School> schools, int totalCount) {

		public SchoolSearchResult {
			schools = List.copyOf(schools);
			if (totalCount < 0) {
				throw new IllegalArgumentException("학교 검색 전체 건수는 음수일 수 없습니다.");
			}
		}
	}
}
