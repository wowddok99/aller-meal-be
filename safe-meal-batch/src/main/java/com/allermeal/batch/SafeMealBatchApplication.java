package com.allermeal.batch;

import com.allermeal.infra.collection.JdbcCollectionJobRepository;
import com.allermeal.infra.config.MealCollectionConfiguration;
import com.allermeal.infra.config.MinioConfiguration;
import com.allermeal.infra.meal.NeisHttpMealClient;
import com.allermeal.infra.outbox.JdbcOutboxEventRepository;
import com.allermeal.infra.raw.MinioRawPayloadStorage;
import com.allermeal.infra.school.JdbcSchoolRepository;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

@EnableScheduling
@Import({MealCollectionConfiguration.class, MinioConfiguration.class})
@SpringBootApplication(scanBasePackageClasses = {
	SafeMealBatchApplication.class,
	JdbcCollectionJobRepository.class,
	NeisHttpMealClient.class,
	JdbcOutboxEventRepository.class,
	MinioRawPayloadStorage.class,
	JdbcSchoolRepository.class
})
public class SafeMealBatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(SafeMealBatchApplication.class, args);
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}
