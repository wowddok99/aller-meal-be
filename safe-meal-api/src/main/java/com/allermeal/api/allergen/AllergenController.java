package com.allermeal.api.allergen;

import com.allermeal.application.allergen.AllergenQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/allergens")
public final class AllergenController {

	private final AllergenQueryService queryService;

	public AllergenController(AllergenQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping
	public List<AllergenResponse> findAll() {
		return queryService.findAll().stream()
			.map(allergen -> new AllergenResponse(allergen.code(), allergen.name()))
			.toList();
	}

	public record AllergenResponse(int code, String name) {
	}
}
