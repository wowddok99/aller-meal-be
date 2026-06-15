package com.allermeal.api.school;

import com.allermeal.api.school.response.SchoolResponse;
import com.allermeal.api.school.response.SchoolSearchResponse;
import com.allermeal.application.port.out.result.SchoolSearchResult;
import com.allermeal.application.school.SchoolSearchService;
import com.allermeal.domain.school.SchoolId;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
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

	@GetMapping("/{schoolId}")
	public SchoolResponse findById(@PathVariable UUID schoolId) {
		return SchoolResponse.from(searchService.findById(new SchoolId(schoolId)));
	}

}
