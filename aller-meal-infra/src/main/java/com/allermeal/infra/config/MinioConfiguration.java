package com.allermeal.infra.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfiguration {

	@Bean
	MinioClient minioClient(
		@Value("${aller-meal.minio.endpoint:http://localhost:9000}") String endpoint,
		@Value("${aller-meal.minio.access-key:aller_meal}") String accessKey,
		@Value("${aller-meal.minio.secret-key:local-minio-password}") String secretKey
	) {
		return MinioClient.builder()
			.endpoint(endpoint)
			.credentials(accessKey, secretKey)
			.build();
	}
}
