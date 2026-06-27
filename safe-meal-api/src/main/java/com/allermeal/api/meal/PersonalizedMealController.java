package com.allermeal.api.meal;

import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.api.meal.response.PersonalizedMealQueryResponse;
import com.allermeal.application.meal.PersonalizedMealQueryResult;
import com.allermeal.application.meal.PersonalizedMealQueryService;
import com.allermeal.application.meal.PublicMealCollectionStatus;
import com.allermeal.application.meal.PublicMealQueryService;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Objects;
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
@RequestMapping("/api/v1/children/{childId}/meals")
public final class PersonalizedMealController {

	private final PersonalizedMealQueryService queryService;

	public PersonalizedMealController(PersonalizedMealQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping("/today")
	public ResponseEntity<PersonalizedMealQueryResponse> findToday(
		HttpServletRequest servletRequest,
		@PathVariable UUID childId
	) {
		return response(queryService.findToday(currentUser(servletRequest).id(), new ChildProfileId(childId)));
	}

	@GetMapping("/{date}")
	public ResponseEntity<PersonalizedMealQueryResponse> findDaily(
		HttpServletRequest servletRequest,
		@PathVariable UUID childId,
		@PathVariable LocalDate date
	) {
		return response(queryService.findDaily(currentUser(servletRequest).id(), new ChildProfileId(childId), date));
	}

	@GetMapping("/weekly")
	public ResponseEntity<PersonalizedMealQueryResponse> findWeekly(
		HttpServletRequest servletRequest,
		@PathVariable UUID childId,
		@RequestParam LocalDate date
	) {
		return response(queryService.findWeekly(currentUser(servletRequest).id(), new ChildProfileId(childId), date));
	}

	private ResponseEntity<PersonalizedMealQueryResponse> response(PersonalizedMealQueryResult result) {
		if (result.collectionStatus() == PublicMealCollectionStatus.READY) {
			return ResponseEntity.ok(PersonalizedMealQueryResponse.from(result));
		}
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(PublicMealQueryService.RETRY_AFTER_SECONDS))
			.body(PersonalizedMealQueryResponse.from(result));
	}

	private User currentUser(HttpServletRequest request) {
		return (User) Objects.requireNonNull(request.getAttribute(AuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE));
	}
}
