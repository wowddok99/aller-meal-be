package com.allermeal.api.child;

import com.allermeal.api.auth.AuthenticationFilter;
import com.allermeal.api.child.request.CreateChildProfileRequest;
import com.allermeal.api.child.request.ReplaceChildAllergensRequest;
import com.allermeal.api.child.request.UpdateChildProfileRequest;
import com.allermeal.api.child.request.UpdateChildNotificationPreferenceRequest;
import com.allermeal.api.child.response.ChildAllergenResponse;
import com.allermeal.api.child.response.ChildNotificationPreferenceResponse;
import com.allermeal.api.child.response.ChildProfileResponse;
import com.allermeal.application.child.ChildAllergenService;
import com.allermeal.application.child.ChildNotificationPreferenceService;
import com.allermeal.application.child.ChildProfileService;
import com.allermeal.application.child.InvalidChildProfileRequestException;
import com.allermeal.application.child.InvalidChildNotificationPreferenceRequestException;
import com.allermeal.application.child.CreateChildProfileCommand;
import com.allermeal.application.child.ReplaceChildAllergensCommand;
import com.allermeal.application.child.UpdateChildProfileCommand;
import com.allermeal.application.child.UpdateChildNotificationPreferenceCommand;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.school.SchoolId;
import com.allermeal.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;

@RestController
@RequestMapping("/api/v1/children")
public final class ChildProfileController {

	private final ChildProfileService childProfileService;
	private final ChildAllergenService childAllergenService;
	private final ChildNotificationPreferenceService childNotificationPreferenceService;

	public ChildProfileController(ChildProfileService childProfileService, ChildAllergenService childAllergenService,
		ChildNotificationPreferenceService childNotificationPreferenceService) {
		this.childProfileService = childProfileService;
		this.childAllergenService = childAllergenService;
		this.childNotificationPreferenceService = childNotificationPreferenceService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ChildProfileResponse create(HttpServletRequest servletRequest, @RequestBody CreateChildProfileRequest request) {
		return ChildProfileResponse.from(childProfileService.create(currentUser(servletRequest).id(), createCommand(request)));
	}

	@GetMapping
	public List<ChildProfileResponse> findAll(HttpServletRequest servletRequest) {
		return childProfileService.findAll(currentUser(servletRequest).id()).stream().map(ChildProfileResponse::from).toList();
	}

	@GetMapping("/{childId}")
	public ChildProfileResponse find(HttpServletRequest servletRequest, @PathVariable UUID childId) {
		return ChildProfileResponse.from(childProfileService.find(currentUser(servletRequest).id(), new ChildProfileId(childId)));
	}

	@PatchMapping("/{childId}")
	public ChildProfileResponse update(HttpServletRequest servletRequest, @PathVariable UUID childId,
		@RequestBody UpdateChildProfileRequest request) {
		return ChildProfileResponse.from(childProfileService.update(currentUser(servletRequest).id(), new ChildProfileId(childId), updateCommand(request)));
	}

	@PutMapping("/{childId}/allergens")
	public ChildAllergenResponse replaceAllergens(HttpServletRequest servletRequest, @PathVariable UUID childId,
		@RequestBody ReplaceChildAllergensRequest request) {
		if (request == null) throw new InvalidChildProfileRequestException();
		return ChildAllergenResponse.from(childAllergenService.replace(currentUser(servletRequest).id(), new ChildProfileId(childId),
			new ReplaceChildAllergensCommand(request.allergenCodes())));
	}

	@GetMapping("/{childId}/notification-preference")
	public ChildNotificationPreferenceResponse findNotificationPreference(HttpServletRequest servletRequest,
		@PathVariable UUID childId) {
		return ChildNotificationPreferenceResponse.from(childNotificationPreferenceService.find(currentUser(servletRequest).id(),
			new ChildProfileId(childId)));
	}

	@PutMapping("/{childId}/notification-preference")
	public ChildNotificationPreferenceResponse updateNotificationPreference(HttpServletRequest servletRequest,
		@PathVariable UUID childId, @RequestBody UpdateChildNotificationPreferenceRequest request) {
		if (request == null || request.emailEnabled() == null || request.notificationTime() == null || request.timezone() == null) {
			throw new InvalidChildNotificationPreferenceRequestException();
		}
		return ChildNotificationPreferenceResponse.from(childNotificationPreferenceService.update(currentUser(servletRequest).id(),
			new ChildProfileId(childId), new UpdateChildNotificationPreferenceCommand(request.emailEnabled(),
				request.notificationTime(), request.timezone())));
	}

	@DeleteMapping("/{childId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(HttpServletRequest servletRequest, @PathVariable UUID childId) {
		childProfileService.delete(currentUser(servletRequest).id(), new ChildProfileId(childId));
	}

	private User currentUser(HttpServletRequest request) {
		return (User) Objects.requireNonNull(request.getAttribute(AuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE));
	}

	private CreateChildProfileCommand createCommand(CreateChildProfileRequest request) {
		if (request == null || request.schoolId() == null) throw new InvalidChildProfileRequestException();
		return new CreateChildProfileCommand(request.name(), request.grade(), request.classNumber(), new SchoolId(request.schoolId()));
	}

	private UpdateChildProfileCommand updateCommand(UpdateChildProfileRequest request) {
		if (request == null || request.schoolId() == null) throw new InvalidChildProfileRequestException();
		return new UpdateChildProfileCommand(request.name(), request.grade(), request.classNumber(), new SchoolId(request.schoolId()));
	}
}
