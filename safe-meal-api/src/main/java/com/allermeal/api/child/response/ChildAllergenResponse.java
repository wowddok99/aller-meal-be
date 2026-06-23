package com.allermeal.api.child.response;

import com.allermeal.application.child.ChildAllergenResult;
import java.util.List;
import java.util.UUID;

public record ChildAllergenResponse(UUID childId, List<Integer> allergenCodes) {

	public ChildAllergenResponse {
		allergenCodes = List.copyOf(allergenCodes);
	}

	public static ChildAllergenResponse from(ChildAllergenResult result) {
		return new ChildAllergenResponse(result.childProfileId().value(), result.allergenCodes());
	}
}
