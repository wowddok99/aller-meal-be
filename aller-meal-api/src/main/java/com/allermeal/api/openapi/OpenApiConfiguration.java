package com.allermeal.api.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class OpenApiConfiguration {

	private static final String ACCESS_COOKIE_AUTH = "accessCookieAuth";
	private static final String REFRESH_COOKIE_AUTH = "refreshCookieAuth";
	private static final String BEARER_AUTH = "bearerAuth";
	private static final String CSRF_HEADER = "csrfHeader";
	private static final String TRACE_ID_HEADER = "X-Trace-Id";
	private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
	private static final String RETRY_AFTER_HEADER = HttpHeaders.RETRY_AFTER;

	private static final Map<String, String> OPERATION_IDS = Map.ofEntries(
		Map.entry("AllergenController#findAll", "listAllergens"),
		Map.entry("AuthController#signup", "signUp"),
		Map.entry("AuthController#requestEmailVerification", "requestEmailVerification"),
		Map.entry("AuthController#confirmEmailVerification", "confirmEmailVerification"),
		Map.entry("AuthController#login", "login"),
		Map.entry("AuthController#requestPasswordReset", "requestPasswordReset"),
		Map.entry("AuthController#confirmPasswordReset", "confirmPasswordReset"),
		Map.entry("AuthController#refresh", "refreshAuthentication"),
		Map.entry("AuthController#logout", "logout"),
		Map.entry("SchoolController#search", "searchPublicSchools"),
		Map.entry("SchoolController#findById", "getPublicSchool"),
		Map.entry("PublicMealController#findDaily", "getPublicDailyMeal"),
		Map.entry("PublicMealController#findWeekly", "getPublicWeeklyMeals"),
		Map.entry("ChildProfileController#create", "createChild"),
		Map.entry("ChildProfileController#findAll", "listChildren"),
		Map.entry("ChildProfileController#find", "getChild"),
		Map.entry("ChildProfileController#update", "updateChild"),
		Map.entry("ChildProfileController#replaceAllergens", "replaceChildAllergens"),
		Map.entry("ChildProfileController#findAllergens", "getChildAllergens"),
		Map.entry("ChildProfileController#findNotificationPreference", "getChildNotificationPreference"),
		Map.entry("ChildProfileController#updateNotificationPreference", "updateChildNotificationPreference"),
		Map.entry("ChildProfileController#delete", "deleteChild"),
		Map.entry("PersonalizedMealController#findToday", "getPersonalizedTodayMeal"),
		Map.entry("PersonalizedMealController#findDaily", "getPersonalizedDailyMeal"),
		Map.entry("PersonalizedMealController#findWeekly", "getPersonalizedWeeklyMeals"),
		Map.entry("NotificationHistoryController#findByChild", "getChildNotificationHistory"),
		Map.entry("AccountWithdrawalController#findWithdrawal", "getAccountWithdrawal"),
		Map.entry("AccountWithdrawalController#requestWithdrawal", "requestAccountWithdrawal"),
		Map.entry("AccountWithdrawalController#cancelWithdrawal", "cancelAccountWithdrawal"),
		Map.entry("AdminCollectionFailureController#findFailedCollectionJobs", "listFailedCollectionJobs"),
		Map.entry("AdminCollectionFailureController#findCollectionJobs", "listAdminCollectionJobs"),
		Map.entry("AdminCollectionFailureController#findMealItemLabelings", "listAdminMealItemLabelings"),
		Map.entry("AdminCollectionFailureController#findExternalApiLogs", "listExternalApiLogs"),
		Map.entry("AdminCollectionFailureController#requestRecollection", "requestCollectionRecollection"),
		Map.entry("AdminNotificationFailureController#findFailedNotifications", "listFailedNotifications"),
		Map.entry("AdminNotificationFailureController#findOutboxEvents", "listAdminOutboxEvents"),
		Map.entry("AdminNotificationFailureController#findNotificationRequests", "listAdminNotificationRequests"),
		Map.entry("AdminNotificationFailureController#findDeadLetterEvents", "listNotificationDeadLetterEvents"),
		Map.entry("AdminNotificationFailureController#reprocessDeadLetterEvent", "reprocessNotificationDeadLetterEvent"),
		Map.entry("AdminDashboardSummaryController#getSummary", "getAdminDashboardSummary"),
		Map.entry("AdminUserController#promoteToAdmin", "promoteUserToAdmin"),
		Map.entry("AdminUserController#changeSuspension", "changeAdminUserSuspension"),
		Map.entry("AdminUserController#findUsers", "listAdminUsers"),
		Map.entry("AdminUserController#findUser", "getAdminUser"),
		Map.entry("AdminUserController#findAccessHistory", "getAdminUserAccessHistory")
	);

	@Bean
	OpenAPI allerMealOpenApi() {
		return new OpenAPI()
			.info(new Info().title("Aller Meal API").version("1.0.0")
				.description("Aller Meal Release 1 public, member, and admin API contract."))
			.components(new Components()
				.addSecuritySchemes(ACCESS_COOKIE_AUTH, cookieAuth("access_token"))
				.addSecuritySchemes(REFRESH_COOKIE_AUTH, cookieAuth("refresh_token"))
				.addSecuritySchemes(BEARER_AUTH, new SecurityScheme().type(SecurityScheme.Type.HTTP)
					.scheme("bearer").bearerFormat("JWT")
					.description("Access Token issued by the login or refresh API."))
				.addSecuritySchemes(CSRF_HEADER, new SecurityScheme().type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.HEADER).name("X-CSRF-Token")
					.description("Required with the csrf_token cookie for non-GET authenticated cookie requests."))
				.addSchemas("ApiErrorResponse", apiErrorResponseSchema()));
	}

	@Bean
	GroupedOpenApi publicApi(OperationCustomizer allerMealOperationCustomizer) {
		return GroupedOpenApi.builder().group("public")
			.pathsToMatch("/api/v1/allergens", "/api/v1/public/**", "/api/v1/auth/**")
			.addOperationCustomizer(allerMealOperationCustomizer).build();
	}

	@Bean
	GroupedOpenApi memberApi(OperationCustomizer allerMealOperationCustomizer) {
		return GroupedOpenApi.builder().group("member")
			.pathsToMatch("/api/v1/children/**", "/api/v1/account/**")
			.addOperationCustomizer(allerMealOperationCustomizer).build();
	}

	@Bean
	GroupedOpenApi adminApi(
		OperationCustomizer allerMealOperationCustomizer,
		OpenApiCustomizer adminLegacyAccessHistoryNullableCustomizer
	) {
		return GroupedOpenApi.builder().group("admin").pathsToMatch("/api/v1/admin/**")
			.addOperationCustomizer(allerMealOperationCustomizer)
			.addOpenApiCustomizer(adminLegacyAccessHistoryNullableCustomizer).build();
	}

	@Bean
	@SuppressWarnings("unchecked")
	OpenApiCustomizer adminLegacyAccessHistoryNullableCustomizer() {
		return openApi -> {
			Schema<?> historyItem = openApi.getComponents().getSchemas().get("AdminUserAccessHistoryItemResponse");
			if (historyItem == null || historyItem.getProperties() == null) return;
			for (String propertyName : List.of("beforeRole", "afterRole", "beforeStatus", "afterStatus")) {
				Schema<?> property = historyItem.getProperties().get(propertyName);
				if (property != null && property.getEnum() != null && !property.getEnum().contains(null)) {
					property.addEnumItemObject(null);
				}
			}
		};
	}

	@Bean
	OperationCustomizer allerMealOperationCustomizer() {
		return (operation, handlerMethod) -> {
			String contractKey = contractKey(handlerMethod);
			operation.setOperationId(requiredOperationId(contractKey));
			boolean csrfRequired = requiresCsrf(handlerMethod);
			boolean authenticated = requiresAccessAuthentication(handlerMethod);
			if (authenticated) {
				addAccessSecurity(operation, csrfRequired);
				operation.addTagsItem(contractKey.startsWith("Admin") ? "admin" : "member");
			}
			if (csrfRequired) addCsrfParameters(operation);
			if (requiresRefreshAuthentication(contractKey)) addRefreshSecurity(operation, csrfRequired);
			addTraceIdParameter(operation);
			addEndpointErrorResponses(operation, handlerMethod, contractKey, authenticated, csrfRequired);
			if (isMealQuery(contractKey)) addMealAcceptedResponse(operation);
			if (contractKey.equals("AccountWithdrawalController#findWithdrawal")) addWithdrawalNotFoundResponse(operation);
			if (writesAuthenticationCookies(contractKey)) {
				addSetCookieResponseHeader(operation, "access_token, refresh_token, csrf_token 쿠키를 각각 Set-Cookie 헤더로 설정합니다.");
			}
			if (clearsAuthenticationCookies(contractKey)) {
				addSetCookieResponseHeader(operation, "access_token, refresh_token, csrf_token 쿠키를 각각 만료 Set-Cookie 헤더로 삭제합니다.");
			}
			if (requiresIdempotencyKey(contractKey)) describeIdempotencyKey(operation);
			addTraceIdResponseHeader(operation);
			return operation;
		};
	}

	private String contractKey(HandlerMethod handlerMethod) {
		return handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
	}

	private String requiredOperationId(String contractKey) {
		String operationId = OPERATION_IDS.get(contractKey);
		if (operationId == null) throw new IllegalStateException("OpenAPI operationId 계약이 없습니다: " + contractKey);
		return operationId;
	}

	private boolean requiresAccessAuthentication(HandlerMethod handlerMethod) {
		String packageName = handlerMethod.getBeanType().getPackageName();
		return packageName.contains(".admin") || packageName.contains(".child") || packageName.contains(".account")
			|| packageName.contains(".notification") || handlerMethod.getBeanType().getSimpleName().equals("PersonalizedMealController");
	}

	private boolean requiresRefreshAuthentication(String contractKey) {
		return contractKey.equals("AuthController#refresh") || contractKey.equals("AuthController#logout");
	}

	private boolean isMealQuery(String contractKey) {
		return contractKey.startsWith("PublicMealController#") || contractKey.startsWith("PersonalizedMealController#");
	}

	private boolean writesAuthenticationCookies(String contractKey) {
		return contractKey.equals("AuthController#login") || contractKey.equals("AuthController#refresh");
	}

	private boolean clearsAuthenticationCookies(String contractKey) {
		return contractKey.equals("AuthController#logout");
	}

	private boolean requiresIdempotencyKey(String contractKey) {
		return contractKey.equals("AdminCollectionFailureController#requestRecollection")
			|| contractKey.equals("AdminNotificationFailureController#reprocessDeadLetterEvent");
	}

	private void addEndpointErrorResponses(Operation operation, HandlerMethod handlerMethod, String contractKey,
		boolean authenticated, boolean csrfRequired) {
		addErrorResponse(operation, "500", "서버에서 요청을 처리하지 못했습니다.");
		if (hasClientInput(handlerMethod)) addErrorResponse(operation, "400", "요청 값 또는 형식이 올바르지 않습니다.");
		if (hasRequestBody(handlerMethod)) addErrorResponse(operation, "415", "지원하지 않는 요청 본문 형식입니다.");
		if (authenticated) {
			addErrorResponse(operation, "401", "로그인이 필요합니다.");
			addErrorResponse(operation, "403", "권한 또는 이메일 인증 상태가 올바르지 않습니다.");
		}
		if (csrfRequired) addErrorResponse(operation, "403", "인증 Cookie 사용 시 Origin 또는 CSRF 검증에 실패했습니다.");
		if (contractKey.startsWith("AuthController#")) addErrorResponse(operation, "429", "인증 API 요청이 너무 많습니다.");
		switch (contractKey) {
			case "AuthController#signup" -> addErrorResponse(operation, "409", "이미 가입된 이메일입니다.");
			case "AuthController#requestEmailVerification" -> {
				addErrorResponse(operation, "404", "요청한 이메일 계정을 찾을 수 없습니다.");
				addErrorResponse(operation, "409", "이미 인증된 이메일입니다.");
			}
			case "AuthController#login" -> {
				addErrorResponse(operation, "401", "이메일 또는 비밀번호가 올바르지 않습니다.");
				addErrorResponse(operation, "403", "이메일 인증 후 이용해 주세요.");
			}
			case "AuthController#refresh" -> addErrorResponse(operation, "401", "유효한 Refresh Token이 필요합니다.");
			case "SchoolController#search" -> addErrorResponse(operation, "502", "학교 정보 제공 기관 요청 또는 응답 처리에 실패했습니다.");
			case "SchoolController#findById", "PublicMealController#findDaily", "PublicMealController#findWeekly",
				"ChildProfileController#create", "ChildProfileController#find", "ChildProfileController#update",
				"ChildProfileController#replaceAllergens", "ChildProfileController#findAllergens",
				"ChildProfileController#findNotificationPreference", "ChildProfileController#updateNotificationPreference",
				"ChildProfileController#delete", "PersonalizedMealController#findToday",
				"PersonalizedMealController#findDaily", "PersonalizedMealController#findWeekly",
				"NotificationHistoryController#findByChild" ->
				addErrorResponse(operation, "404", "요청한 리소스를 찾을 수 없습니다.");
			case "AccountWithdrawalController#requestWithdrawal" -> addErrorResponse(operation, "409", "탈퇴 상태를 변경할 수 없습니다.");
			case "AdminCollectionFailureController#requestRecollection" ->
				addNotFoundAndConflict(operation, "Idempotency-Key가 다른 재수집 요청에 이미 사용되었습니다.");
			case "AdminNotificationFailureController#reprocessDeadLetterEvent" ->
				addNotFoundAndConflict(operation, "DLQ 재처리 요청이 이미 처리되었거나 충돌했습니다.");
			case "AdminUserController#promoteToAdmin", "AdminUserController#changeSuspension" ->
				addNotFoundAndConflict(operation, "사용자 상태가 변경되어 요청을 처리할 수 없습니다.");
			case "AdminUserController#findUser", "AdminUserController#findAccessHistory" ->
				addErrorResponse(operation, "404", "요청한 리소스를 찾을 수 없습니다.");
			default -> { }
		}
	}

	private void addNotFoundAndConflict(Operation operation, String conflictDescription) {
		addErrorResponse(operation, "404", "요청한 리소스를 찾을 수 없습니다.");
		addErrorResponse(operation, "409", conflictDescription);
	}

	private boolean hasClientInput(HandlerMethod handlerMethod) {
		return Arrays.stream(handlerMethod.getMethod().getParameters()).anyMatch(parameter ->
			parameter.isAnnotationPresent(RequestBody.class) || parameter.isAnnotationPresent(RequestParam.class)
				|| parameter.isAnnotationPresent(PathVariable.class) || parameter.isAnnotationPresent(RequestHeader.class));
	}

	private boolean hasRequestBody(HandlerMethod handlerMethod) {
		return Arrays.stream(handlerMethod.getMethod().getParameters()).anyMatch(parameter -> parameter.isAnnotationPresent(RequestBody.class));
	}

	private void addErrorResponse(Operation operation, String status, String description) {
		if (!operation.getResponses().containsKey(status)) operation.getResponses().addApiResponse(status, error(description));
	}

	private void addMealAcceptedResponse(Operation operation) {
		ApiResponse readyResponse = operation.getResponses().get("200");
		ApiResponse acceptedResponse = new ApiResponse().description("급식 수집이 진행 중입니다. Retry-After 초 후 같은 요청을 다시 시도해 주세요.")
			.content(readyResponse.getContent()).addHeaderObject(RETRY_AFTER_HEADER, new Header()
				.description("재조회까지 기다릴 시간(초)입니다.").schema(new IntegerSchema().minimum(BigDecimal.ONE).example(3)));
		operation.getResponses().addApiResponse("202", acceptedResponse);
	}

	private void addWithdrawalNotFoundResponse(Operation operation) {
		operation.getResponses().addApiResponse("204", new ApiResponse()
			.description("진행 중인 탈퇴 예약이 없습니다."));
	}

	private void addSetCookieResponseHeader(Operation operation, String description) {
		for (String successStatus : List.of("200", "204")) {
			ApiResponse response = operation.getResponses().get(successStatus);
			if (response != null) response.addHeaderObject(HttpHeaders.SET_COOKIE,
				new Header().description(description).schema(new StringSchema()));
		}
	}

	private void addTraceIdResponseHeader(Operation operation) {
		operation.getResponses().values().forEach(response -> response.addHeaderObject(TRACE_ID_HEADER,
			new Header().description("서버가 반환한 요청 추적 ID입니다.").schema(new StringSchema())));
	}

	private void addTraceIdParameter(Operation operation) {
		operation.addParametersItem(new Parameter().in("header").name(TRACE_ID_HEADER).required(false)
			.description("선택한 요청 추적 ID입니다. 영문자, 숫자, '.', '_', '-' 1~128자만 허용됩니다.").schema(new StringSchema()));
	}

	private void addCsrfParameters(Operation operation) {
		operation.addParametersItem(new Parameter().in("header").name("X-CSRF-Token").required(false)
			.description("access_token 또는 refresh_token Cookie를 보내는 변경 요청에서는 csrf_token Cookie와 같은 값이 필요합니다.")
			.schema(new StringSchema()));
		operation.addParametersItem(new Parameter().in("header").name(HttpHeaders.ORIGIN).required(false)
			.description("인증 Cookie를 보내는 변경 요청에서는 허용된 Origin 값이 필요합니다.")
			.schema(new StringSchema().format("uri")));
	}

	private void describeIdempotencyKey(Operation operation) {
		operation.getParameters().stream().filter(parameter -> "header".equals(parameter.getIn())
			&& IDEMPOTENCY_KEY_HEADER.equals(parameter.getName())).forEach(parameter -> parameter.description(
				"동일 작업 재시도에 재사용할 필수 멱등 키입니다. 다른 요청에 재사용하면 409가 반환됩니다."));
	}

	private void addRefreshSecurity(Operation operation, boolean csrfRequired) {
		SecurityRequirement refreshCookie = new SecurityRequirement().addList(REFRESH_COOKIE_AUTH);
		if (csrfRequired) refreshCookie.addList(CSRF_HEADER);
		operation.addSecurityItem(refreshCookie);
	}

	private SecurityScheme cookieAuth(String cookieName) {
		return new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name(cookieName);
	}

	private ApiResponse error(String description) {
		return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
			new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))));
	}

	private void addAccessSecurity(Operation operation, boolean csrfRequired) {
		operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
		SecurityRequirement cookieRequirement = new SecurityRequirement().addList(ACCESS_COOKIE_AUTH);
		if (csrfRequired) cookieRequirement.addList(CSRF_HEADER);
		operation.addSecurityItem(cookieRequirement);
	}

	private boolean requiresCsrf(HandlerMethod handlerMethod) {
		if (handlerMethod.hasMethodAnnotation(GetMapping.class)) return false;
		if (handlerMethod.hasMethodAnnotation(PostMapping.class) || handlerMethod.hasMethodAnnotation(PutMapping.class)
			|| handlerMethod.hasMethodAnnotation(PatchMapping.class) || handlerMethod.hasMethodAnnotation(DeleteMapping.class)) return true;
		RequestMapping requestMapping = handlerMethod.getMethodAnnotation(RequestMapping.class);
		return requestMapping != null && List.of(requestMapping.method()).stream().anyMatch(method ->
			!"GET".equals(method.name()) && !"HEAD".equals(method.name()) && !"OPTIONS".equals(method.name()));
	}

	private Schema<?> apiErrorResponseSchema() {
		Schema<Object> error = new ObjectSchema().addProperty("code", new StringSchema().example("INVALID_REQUEST"))
			.addProperty("message", new StringSchema().example("요청 형식 또는 값이 올바르지 않습니다."))
			.addProperty("details", new ObjectSchema()).addProperty("traceId",
				new StringSchema().example("7b3f6d8e-1d2c-4f5a-9b0c-123456789abc"));
		error.required(List.of("code", "message", "details", "traceId"));
		Schema<Object> response = new ObjectSchema().addProperty("error", error);
		response.required(List.of("error"));
		return response;
	}
}
