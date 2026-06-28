package com.allermeal.application.child;

import com.allermeal.application.port.out.AllergenRepository;
import com.allermeal.application.port.out.ChildAllergenRepository;
import com.allermeal.application.port.out.ChildProfileRepository;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.user.UserId;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class ChildAllergenService {

	private final ChildProfileRepository childProfileRepository;
	private final ChildAllergenRepository childAllergenRepository;
	private final AllergenRepository allergenRepository;

	public ChildAllergenService(
		ChildProfileRepository childProfileRepository,
		ChildAllergenRepository childAllergenRepository,
		AllergenRepository allergenRepository
	) {
		this.childProfileRepository = childProfileRepository;
		this.childAllergenRepository = childAllergenRepository;
		this.allergenRepository = allergenRepository;
	}

	public ChildAllergenResult replace(UserId ownerId, ChildProfileId childProfileId, ReplaceChildAllergensCommand command) {
		if (childProfileRepository.findByIdAndOwnerId(childProfileId, ownerId).isEmpty()) {
			throw new ChildProfileNotFoundException();
		}
		List<Integer> allergenCodes = normalize(command);
		if (!childAllergenRepository.replaceAll(ownerId, childProfileId, allergenCodes)) {
			throw new ChildProfileNotFoundException();
		}
		return new ChildAllergenResult(childProfileId, allergenCodes);
	}

	private List<Integer> normalize(ReplaceChildAllergensCommand command) {
		try {
			Set<Integer> allergenCodes = new TreeSet<>(command.allergenCodes());
			if (allergenCodes.stream().anyMatch(code -> code == null || code <= 0)) {
				throw new InvalidChildAllergenRequestException();
			}
			Set<Integer> validCodes = allergenRepository.findAll().stream().map(allergen -> allergen.code()).collect(java.util.stream.Collectors.toSet());
			if (!validCodes.containsAll(allergenCodes)) throw new InvalidChildAllergenRequestException();
			return List.copyOf(allergenCodes);
		} catch (NullPointerException exception) {
			throw new InvalidChildAllergenRequestException();
		}
	}
}
