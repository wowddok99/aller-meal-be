package com.allermeal.api.meal;

import com.allermeal.api.meal.response.PublicMealQueryResponse;
import com.allermeal.application.meal.PublicMealCollectionStatus;
import com.allermeal.application.meal.PublicMealQueryResult;
import com.allermeal.application.meal.PublicMealQueryService;
import com.allermeal.domain.school.SchoolId;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/schools/{schoolId}/meals")
public final class PublicMealController {

	private final PublicMealQueryService queryService;

	public PublicMealController(PublicMealQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping("/{date}")
	public ResponseEntity<PublicMealQueryResponse> findDaily(
		@PathVariable UUID schoolId,
		@PathVariable LocalDate date
	) {
		return response(queryService.findDaily(new SchoolId(schoolId), date));
	}

	@GetMapping("/weekly")
	public ResponseEntity<PublicMealQueryResponse> findWeekly(
		@PathVariable UUID schoolId,
		@RequestParam LocalDate date
	) {
		return response(queryService.findWeekly(new SchoolId(schoolId), date));
	}

	private ResponseEntity<PublicMealQueryResponse> response(PublicMealQueryResult result) {
		if (result.collectionStatus() == PublicMealCollectionStatus.READY) {
			return ResponseEntity.ok(PublicMealQueryResponse.from(result));
		}
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(PublicMealQueryService.RETRY_AFTER_SECONDS))
			.body(PublicMealQueryResponse.from(result));
	}
}
