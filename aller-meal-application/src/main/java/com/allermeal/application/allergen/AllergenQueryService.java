package com.allermeal.application.allergen;

import com.allermeal.application.port.out.AllergenRepository;
import com.allermeal.domain.allergen.Allergen;
import java.util.List;

public final class AllergenQueryService {

	private final AllergenRepository repository;

	public AllergenQueryService(AllergenRepository repository) {
		this.repository = repository;
	}

	public List<Allergen> findAll() {
		return repository.findAll();
	}
}
