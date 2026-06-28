package com.allermeal.application.child;

import java.util.List;

public record ReplaceChildAllergensCommand(List<Integer> allergenCodes) {

	public ReplaceChildAllergensCommand {
		try {
			allergenCodes = List.copyOf(allergenCodes);
		} catch (NullPointerException exception) {
			throw new InvalidChildAllergenRequestException();
		}
	}
}
