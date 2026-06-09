package com.allermeal.api.school;

import com.allermeal.application.port.out.NeisSchoolClient.SchoolSearchResult;
import com.allermeal.application.school.SchoolSearchService;
import com.allermeal.domain.school.School;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/schools")
public final class SchoolController {

	private final SchoolSearchService searchService;

	public SchoolController(SchoolSearchService searchService) {
		this.searchService = searchService;
	}

	@GetMapping
	public SchoolSearchResponse search(
		@RequestParam String keyword,
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "20") int pageSize
	) {
		SchoolSearchResult result = searchService.search(keyword, page, pageSize);
		return new SchoolSearchResponse(
			result.schools().stream().map(SchoolResponse::from).toList(),
			page,
			pageSize,
			result.totalCount());
	}

	public record SchoolSearchResponse(List<SchoolResponse> schools, int page, int pageSize, int totalCount) {
	}

	public record SchoolResponse(
		UUID id,
		String neisSchoolCode,
		String educationOfficeCode,
		String name,
		String address,
		String region
	) {
		private static SchoolResponse from(School school) {
			return new SchoolResponse(
				school.id().value(),
				school.neisSchoolCode(),
				school.educationOfficeCode(),
				school.name(),
				school.address(),
				school.region());
		}
	}
}
