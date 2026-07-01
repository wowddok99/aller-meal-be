package com.allermeal.infra.health;

import io.minio.MinioClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public final class MinioHealthIndicator implements HealthIndicator {

	private final MinioClient minioClient;

	public MinioHealthIndicator(MinioClient minioClient) {
		this.minioClient = minioClient;
	}

	@Override
	public Health health() {
		try {
			minioClient.listBuckets();
			return Health.up().build();
		} catch (Exception exception) {
			return Health.down(exception).build();
		}
	}
}
