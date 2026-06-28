package com.allermeal.infra.school;

import com.allermeal.application.port.out.NeisSchoolClient;
import com.allermeal.application.port.out.result.SchoolSearchResult;
import com.allermeal.application.school.NeisApiException;
import com.allermeal.application.school.NeisInvalidResponseException;
import com.allermeal.domain.school.School;
import com.allermeal.domain.school.SchoolId;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class NeisHttpSchoolClient implements NeisSchoolClient {

	private static final String SUCCESS_CODE = "INFO-000";
	private static final String NO_DATA_CODE = "INFO-200";

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final String baseUrl;
	private final String apiKey;
	private final Duration requestTimeout;

	public NeisHttpSchoolClient(
		@Value("${aller-meal.neis.base-url:https://open.neis.go.kr/hub}") String baseUrl,
		@Value("${aller-meal.neis.api-key:}") String apiKey,
		@Value("${aller-meal.neis.request-timeout:5s}") Duration requestTimeout
	) {
		this.httpClient = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
		this.objectMapper = new ObjectMapper();
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.requestTimeout = requestTimeout;
	}

	@Override
	public SchoolSearchResult search(String keyword, int page, int pageSize) {
		HttpRequest request = HttpRequest.newBuilder(buildUri(keyword, page, pageSize))
			.timeout(requestTimeout)
			.GET()
			.build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new NeisApiException("HTTP_" + response.statusCode(), "NEIS 학교 검색 호출이 실패했습니다.");
			}
			return parse(response.body());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new NeisApiException("INTERRUPTED", "NEIS 학교 검색 호출이 중단되었습니다.", exception);
		} catch (IOException exception) {
			throw new NeisApiException("IO_ERROR", "NEIS 학교 검색 호출에 실패했습니다.", exception);
		}
	}

	private SchoolSearchResult parse(String body) {
		final JsonNode root;
		try {
			root = objectMapper.readTree(body);
		} catch (RuntimeException exception) {
			throw new NeisInvalidResponseException("NEIS 학교 검색 응답이 올바른 JSON이 아닙니다.", exception);
		}
		if (root == null || !root.isObject()) {
			throw new NeisInvalidResponseException("NEIS 학교 검색 응답 최상위 구조가 올바르지 않습니다.");
		}

		JsonNode directResult = root.get("RESULT");
		if (directResult != null) {
			String code = requiredText(directResult, "CODE");
			if (NO_DATA_CODE.equals(code)) {
				return new SchoolSearchResult(List.of(), 0);
			}
			throw new NeisApiException(code, requiredText(directResult, "MESSAGE"));
		}

		JsonNode schoolInfo = root.get("schoolInfo");
		if (schoolInfo == null || !schoolInfo.isArray() || schoolInfo.size() < 2) {
			throw new NeisInvalidResponseException("NEIS 학교 검색 응답에 schoolInfo가 없습니다.");
		}
		JsonNode headContainer = schoolInfo.get(0);
		JsonNode rowContainer = schoolInfo.get(1);
		if (headContainer == null || !headContainer.isObject() || rowContainer == null || !rowContainer.isObject()) {
			throw new NeisInvalidResponseException("NEIS 학교 검색 응답 구조가 올바르지 않습니다.");
		}
		JsonNode head = headContainer.get("head");
		JsonNode rows = rowContainer.get("row");
		if (head == null || !head.isArray() || rows == null || !rows.isArray()) {
			throw new NeisInvalidResponseException("NEIS 학교 검색 응답 구조가 올바르지 않습니다.");
		}
		if (head.size() < 2 || head.get(0) == null || !head.get(0).isObject()
			|| head.get(1) == null || !head.get(1).isObject()) {
			throw new NeisInvalidResponseException("NEIS 학교 검색 응답 head 구조가 올바르지 않습니다.");
		}

		int totalCount = requiredInt(head.get(0), "list_total_count");
		JsonNode result = head.get(1).get("RESULT");
		if (result == null || !result.isObject()) {
			throw new NeisInvalidResponseException("NEIS 학교 검색 응답 RESULT 구조가 올바르지 않습니다.");
		}
		String code = requiredText(result, "CODE");
		if (!SUCCESS_CODE.equals(code)) {
			throw new NeisApiException(code, requiredText(result, "MESSAGE"));
		}

		List<School> schools = new ArrayList<>();
		for (JsonNode row : rows) {
			try {
				schools.add(new School(
					new SchoolId(UUID.randomUUID()),
					requiredText(row, "SD_SCHUL_CODE"),
					requiredText(row, "ATPT_OFCDC_SC_CODE"),
					requiredText(row, "SCHUL_NM"),
					requiredText(row, "ORG_RDNMA"),
					requiredText(row, "LCTN_SC_NM")));
			} catch (IllegalArgumentException exception) {
				throw new NeisInvalidResponseException("NEIS 학교 검색 행의 필수 값이 올바르지 않습니다.", exception);
			}
		}
		return new SchoolSearchResult(schools, totalCount);
	}

	private URI buildUri(String keyword, int page, int pageSize) {
		String query = "Type=json&pIndex=" + page + "&pSize=" + pageSize
			+ "&SCHUL_NM=" + encode(keyword)
			+ (apiKey.isBlank() ? "" : "&KEY=" + encode(apiKey));
		return URI.create(baseUrl + "/schoolInfo?" + query);
	}

	private String requiredText(JsonNode node, String fieldName) {
		if (node == null || node.get(fieldName) == null || !node.get(fieldName).isString()
			|| node.get(fieldName).textValue().isBlank()) {
			throw new NeisInvalidResponseException("NEIS 학교 검색 응답 필드가 올바르지 않습니다: " + fieldName);
		}
		return node.get(fieldName).textValue();
	}

	private int requiredInt(JsonNode node, String fieldName) {
		if (node == null || node.get(fieldName) == null || !node.get(fieldName).isInt()) {
			throw new NeisInvalidResponseException("NEIS 학교 검색 응답 필드가 올바르지 않습니다: " + fieldName);
		}
		return node.get(fieldName).intValue();
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
