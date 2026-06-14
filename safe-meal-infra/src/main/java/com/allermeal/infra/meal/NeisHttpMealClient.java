package com.allermeal.infra.meal;

import com.allermeal.application.meal.MealCollectionException;
import com.allermeal.application.port.out.NeisMealClient;
import com.allermeal.application.port.out.result.RawMealResponse;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.School;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NeisHttpMealClient implements NeisMealClient {

	private static final DateTimeFormatter NEIS_DATE = DateTimeFormatter.BASIC_ISO_DATE;

	private final HttpClient httpClient;
	private final Clock clock;
	private final String baseUrl;
	private final String apiKey;
	private final Duration requestTimeout;
	private final int maxPayloadBytes;
	private final Retry retry;
	private final CircuitBreaker circuitBreaker;

	public NeisHttpMealClient(
		Clock clock,
		@Value("${safe-meal.neis.base-url:https://open.neis.go.kr/hub}") String baseUrl,
		@Value("${safe-meal.neis.api-key:}") String apiKey,
		@Value("${safe-meal.neis.meal.request-timeout:5s}") Duration requestTimeout,
		@Value("${safe-meal.neis.meal.max-payload-bytes:1048576}") int maxPayloadBytes,
		@Value("${safe-meal.neis.meal.max-attempts:3}") int maxAttempts,
		@Value("${safe-meal.neis.meal.initial-backoff:200ms}") Duration initialBackoff,
		@Value("${safe-meal.neis.meal.backoff-multiplier:2.0}") double backoffMultiplier,
		@Value("${safe-meal.neis.meal.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
		@Value("${safe-meal.neis.meal.circuit-breaker.sliding-window-size:10}") int slidingWindowSize,
		@Value("${safe-meal.neis.meal.circuit-breaker.minimum-calls:5}") int minimumCalls,
		@Value("${safe-meal.neis.meal.circuit-breaker.open-duration:30s}") Duration openDuration
	) {
		this.httpClient = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
		this.clock = clock;
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.requestTimeout = requestTimeout;
		if (maxPayloadBytes < 1) {
			throw new IllegalArgumentException("NEIS 급식 최대 payload 크기는 1 이상이어야 합니다.");
		}
		this.maxPayloadBytes = maxPayloadBytes;
		this.retry = Retry.of("neisMeal", RetryConfig.custom()
			.maxAttempts(maxAttempts)
			.intervalFunction(IntervalFunction.ofExponentialBackoff(initialBackoff.toMillis(), backoffMultiplier))
			.retryOnException(this::isRetryable)
			.build());
		this.circuitBreaker = CircuitBreaker.of("neisMeal", CircuitBreakerConfig.custom()
			.failureRateThreshold(failureRateThreshold)
			.slidingWindowSize(slidingWindowSize)
			.minimumNumberOfCalls(minimumCalls)
			.waitDurationInOpenState(openDuration)
			.build());
	}

	@Override
	public RawMealResponse fetch(School school, LocalDate mealDate, MealType mealType) {
		HttpRequest request = HttpRequest.newBuilder(buildUri(school, mealDate, mealType))
			.timeout(requestTimeout)
			.GET()
			.build();
		Supplier<RawMealResponse> retriedCall = Retry.decorateSupplier(retry, () -> execute(request));
		if (!circuitBreaker.tryAcquirePermission()) {
			throw new MealCollectionException("NEIS_CIRCUIT_OPEN", "NEIS 급식 호출 circuit breaker가 열려 있습니다.");
		}
		long startedNanos = System.nanoTime();
		try {
			return retriedCall.get();
		} catch (RuntimeException exception) {
			circuitBreaker.onError(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS, exception);
			throw exception;
		}
	}

	@Override
	public void recordExternalCallSuccess() {
		circuitBreaker.onSuccess(0, TimeUnit.NANOSECONDS);
	}

	@Override
	public void recordValidationSuccess() {
		circuitBreaker.onSuccess(0, TimeUnit.NANOSECONDS);
	}

	@Override
	public void recordValidationFailure(RuntimeException exception) {
		circuitBreaker.onError(0, TimeUnit.NANOSECONDS, exception);
	}

	private RawMealResponse execute(HttpRequest request) {
		long startedNanos = System.nanoTime();
		try {
			HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				closeQuietly(response.body());
				applyRetryAfter(response);
				throw new MealCollectionException(
					httpFailureCode(response.statusCode()), "NEIS 급식 호출이 실패했습니다.");
			}
			return new RawMealResponse(readLimited(response.body()), clock.instant(), elapsedMillis(startedNanos));
		} catch (HttpTimeoutException exception) {
			throw new MealCollectionException("NEIS_TIMEOUT", "NEIS 급식 호출 시간이 초과되었습니다.", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new MealCollectionException("NEIS_INTERRUPTED", "NEIS 급식 호출이 중단되었습니다.", exception);
		} catch (IOException exception) {
			throw new MealCollectionException("NEIS_IO_ERROR", "NEIS 급식 호출에 실패했습니다.", exception);
		}
	}

	private byte[] readLimited(InputStream inputStream) throws IOException {
		try (inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[8192];
			int total = 0;
			int read;
			while ((read = inputStream.read(buffer)) != -1) {
				total += read;
				if (total > maxPayloadBytes) {
					throw new MealCollectionException("NEIS_PAYLOAD_TOO_LARGE", "NEIS 급식 응답 크기가 제한을 초과했습니다.");
				}
				output.write(buffer, 0, read);
			}
			return output.toByteArray();
		}
	}

	private void applyRetryAfter(HttpResponse<?> response) {
		if (response.statusCode() != 408 && response.statusCode() != 429) {
			return;
		}
		long baseMillis = response.headers().firstValue("Retry-After").map(this::retryAfterMillis).orElse(0L);
		long jitterMillis = ThreadLocalRandom.current().nextLong(50, 151);
		try {
			Thread.sleep(Math.min(baseMillis + jitterMillis, requestTimeout.toMillis()));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new MealCollectionException("NEIS_INTERRUPTED", "NEIS 급식 retry 대기가 중단되었습니다.", exception);
		}
	}

	private long retryAfterMillis(String value) {
		try {
			return Math.max(0, Long.parseLong(value.trim()) * 1000);
		} catch (NumberFormatException ignored) {
			try {
				return Math.max(0, Duration.between(
					clock.instant(),
					ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis());
			} catch (DateTimeParseException invalidDate) {
				return 0;
			}
		}
	}

	private String httpFailureCode(int statusCode) {
		return switch (statusCode) {
			case 408 -> "NEIS_HTTP_408";
			case 429 -> "NEIS_HTTP_429";
			default -> statusCode >= 500 ? "NEIS_HTTP_5XX" : "NEIS_HTTP_ERROR";
		};
	}

	private long elapsedMillis(long startedNanos) {
		return Math.max(0, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
	}

	private void closeQuietly(InputStream inputStream) {
		try {
			inputStream.close();
		} catch (IOException ignored) {
			// 응답 실패를 우선 보존한다.
		}
	}

	private URI buildUri(School school, LocalDate mealDate, MealType mealType) {
		String query = "Type=json&pIndex=1&pSize=10"
			+ "&ATPT_OFCDC_SC_CODE=" + encode(school.educationOfficeCode())
			+ "&SD_SCHUL_CODE=" + encode(school.neisSchoolCode())
			+ "&MLSV_YMD=" + NEIS_DATE.format(mealDate)
			+ "&MMEAL_SC_CODE=" + mealTypeCode(mealType)
			+ (apiKey.isBlank() ? "" : "&KEY=" + encode(apiKey));
		return URI.create(baseUrl + "/mealServiceDietInfo?" + query);
	}

	private boolean isRetryable(Throwable throwable) {
		return throwable instanceof MealCollectionException exception
			&& (exception.code().startsWith("NEIS_HTTP_5")
				|| exception.code().equals("NEIS_HTTP_408")
				|| exception.code().equals("NEIS_HTTP_429")
				|| exception.code().equals("NEIS_TIMEOUT")
				|| exception.code().equals("NEIS_IO_ERROR"));
	}

	private String mealTypeCode(MealType mealType) {
		return switch (mealType) {
			case BREAKFAST -> "1";
			case LUNCH -> "2";
			case DINNER -> "3";
		};
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
