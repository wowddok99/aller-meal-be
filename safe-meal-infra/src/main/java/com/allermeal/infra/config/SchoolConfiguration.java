package com.allermeal.infra.config;

import com.allermeal.application.port.out.NeisSchoolClient;
import com.allermeal.application.port.out.SchoolRepository;
import com.allermeal.application.school.SchoolSearchService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SchoolConfiguration {

	@Bean
	SchoolSearchService schoolSearchService(NeisSchoolClient neisSchoolClient, SchoolRepository schoolRepository) {
		return new SchoolSearchService(neisSchoolClient, schoolRepository);
	}
}
