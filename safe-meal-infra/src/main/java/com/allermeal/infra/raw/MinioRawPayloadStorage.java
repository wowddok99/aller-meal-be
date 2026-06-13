package com.allermeal.infra.raw;

import com.allermeal.application.port.out.RawPayloadStorage;
import com.allermeal.application.raw.RawPayloadStorageException;
import com.allermeal.domain.raw.RawObjectMetadata;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class MinioRawPayloadStorage implements RawPayloadStorage {

	private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("uuuu/MM/dd")
		.withZone(ZoneOffset.UTC);

	private final MinioClient minioClient;
	private final JdbcClient jdbcClient;
	private final String bucket;

	public MinioRawPayloadStorage(
		MinioClient minioClient,
		JdbcClient jdbcClient,
		@Value("${safe-meal.minio.raw-bucket:safe-meal-raw}") String bucket
	) {
		this.minioClient = minioClient;
		this.jdbcClient = jdbcClient;
		this.bucket = bucket;
	}

	@Override
	public RawObjectMetadata store(RawPayload payload) {
		byte[] bytes = payload.bytes();
		String hash = sha256(bytes);
		UUID id = UUID.randomUUID();
		String objectKey = payload.source() + "/" + KEY_DATE.format(payload.receivedAt()) + "/" + id + ".json";

		try {
			ensureBucket();
			minioClient.putObject(PutObjectArgs.builder()
				.bucket(bucket)
				.object(objectKey)
				.stream(new ByteArrayInputStream(bytes), (long) bytes.length, -1L)
				.contentType(payload.contentType())
				.userMetadata(Map.of(
					"sha256", hash,
					"expires-at", payload.expiresAt().toString()))
				.build());
		} catch (Exception exception) {
			throw new RawPayloadStorageException("MinIO 원본 payload 저장에 실패했습니다.", exception);
		}

		RawObjectMetadata metadata = new RawObjectMetadata(
			id, objectKey, hash, bytes.length, payload.receivedAt(), payload.expiresAt());
		try {
			jdbcClient.sql("""
					INSERT INTO raw_meal_objects (
					    raw_object_id, object_key, sha256_hash, size_bytes, received_at, expires_at, created_at, updated_at
					)
					VALUES (
					    :id, :objectKey, :hash, :sizeBytes, :receivedAt, :expiresAt, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
					)
					""")
				.param("id", metadata.id())
				.param("objectKey", metadata.objectKey())
				.param("hash", metadata.sha256Hash())
				.param("sizeBytes", metadata.sizeBytes())
				.param("receivedAt", OffsetDateTime.ofInstant(metadata.receivedAt(), ZoneOffset.UTC))
				.param("expiresAt", OffsetDateTime.ofInstant(metadata.expiresAt(), ZoneOffset.UTC))
				.update();
			return metadata;
		} catch (RuntimeException exception) {
			removeOrphanObject(objectKey, exception);
			throw new RawPayloadStorageException("원본 객체 메타데이터 저장에 실패했습니다.", exception);
		}
	}

	private void ensureBucket() throws Exception {
		if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
			try {
				minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
			} catch (Exception exception) {
				if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
					throw exception;
				}
			}
		}
	}

	private void removeOrphanObject(String objectKey, RuntimeException storageException) {
		try {
			minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
		} catch (Exception cleanupException) {
			storageException.addSuppressed(cleanupException);
		}
	}

	private String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
		}
	}
}
