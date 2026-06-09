package com.allermeal.infra.config;

import com.allermeal.application.allergen.AllergenQueryService;
import com.allermeal.application.port.out.AllergenRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AllergenConfiguration {

	@Bean
	AllergenQueryService allergenQueryService(AllergenRepository repository) {
		return new AllergenQueryService(repository);
	}
}
