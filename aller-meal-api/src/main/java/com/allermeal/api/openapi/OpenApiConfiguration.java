package com.allermeal.api.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class OpenApiConfiguration {

	private static final String ACCESS_COOKIE_AUTH = "accessCookieAuth";
	private static final String REFRESH_COOKIE_AUTH = "refreshCookieAuth";
	private static final String BEARER_AUTH = "bearerAuth";
	private static final String CSRF_HEADER = "csrfHeader";

	@Bean
	OpenAPI allerMealOpenApi() {
		return new OpenAPI()
			.info(new Info()
				.title("Aller Meal API")
				.version("1.0.0")
				.description("Aller Meal Release 1 public, member, and admin API contract."))
			.components(new Components()
				.addSecuritySchemes(ACCESS_COOKIE_AUTH, cookieAuth("access_token"))
				.addSecuritySchemes(REFRESH_COOKIE_AUTH, cookieAuth("refresh_token"))
				.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
					.description("Access Token issued by the login or refresh API."))
				.addSecuritySchemes(CSRF_HEADER, new SecurityScheme()
					.type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.HEADER)
					.name("X-CSRF-Token")
					.description("Required for non-GET authenticated requests when auth cookies are used."))
				.addSchemas("ApiErrorResponse", apiErrorResponseSchema()));
	}

	@Bean
	GroupedOpenApi publicApi(OperationCustomizer allerMealOperationCustomizer) {
		return GroupedOpenApi.builder()
			.group("public")
			.pathsToMatch("/api/v1/allergens", "/api/v1/public/**", "/api/v1/auth/**")
			.addOperationCustomizer(allerMealOperationCustomizer)
			.build();
	}

	@Bean
	GroupedOpenApi memberApi(OperationCustomizer allerMealOperationCustomizer) {
		return GroupedOpenApi.builder()
			.group("member")
			.pathsToMatch("/api/v1/children/**", "/api/v1/account/**")
			.addOperationCustomizer(allerMealOperationCustomizer)
			.build();
	}

	@Bean
	GroupedOpenApi adminApi(OperationCustomizer allerMealOperationCustomizer) {
		return GroupedOpenApi.builder()
			.group("admin")
			.pathsToMatch("/api/v1/admin/**")
			.addOperationCustomizer(allerMealOperationCustomizer)
			.build();
	}

	@Bean
	OperationCustomizer allerMealOperationCustomizer() {
		return (operation, handlerMethod) -> {
			String path = handlerMethod.getMethod().toGenericString();
			addCommonErrorResponses(operation);
			boolean csrfRequired = requiresCsrf(handlerMethod);
			if (path.contains(".admin.")) {
				addAccessSecurity(operation, csrfRequired);
				operation.addTagsItem("admin");
			} else if (path.contains(".child.") || path.contains(".account.") || path.contains(".notification.")
				|| path.contains(".meal.Personalized")) {
				addAccessSecurity(operation, csrfRequired);
				operation.addTagsItem("member");
			}
			if (csrfRequired) {
				operation.addParametersItem(new Parameter()
					.in("header")
					.name("X-CSRF-Token")
					.required(false)
					.description("Required with the csrf_token cookie for non-GET authenticated cookie requests."));
			}
			if (path.contains(".auth.AuthController.refresh") || path.contains(".auth.AuthController.logout")) {
				SecurityRequirement refreshCookie = new SecurityRequirement().addList(REFRESH_COOKIE_AUTH);
				if (csrfRequired) {
					refreshCookie.addList(CSRF_HEADER);
				}
				operation.addSecurityItem(refreshCookie);
			}
			return operation;
		};
	}

	private SecurityScheme cookieAuth(String cookieName) {
		return new SecurityScheme()
			.type(SecurityScheme.Type.APIKEY)
			.in(SecurityScheme.In.COOKIE)
			.name(cookieName);
	}

	private void addCommonErrorResponses(Operation operation) {
		operation.getResponses().addApiResponse("400", error("요청 형식 또는 값이 올바르지 않습니다."));
		operation.getResponses().addApiResponse("401", error("로그인이 필요합니다."));
		operation.getResponses().addApiResponse("403", error("권한 또는 이메일 인증 상태가 올바르지 않습니다."));
		operation.getResponses().addApiResponse("404", error("요청한 리소스를 찾을 수 없습니다."));
		operation.getResponses().addApiResponse("405", error("지원하지 않는 요청 방식입니다."));
		operation.getResponses().addApiResponse("409", error("요청 상태가 현재 리소스 상태와 충돌합니다."));
		operation.getResponses().addApiResponse("415", error("지원하지 않는 요청 형식입니다."));
		operation.getResponses().addApiResponse("422", error("요청한 작업을 처리할 수 없습니다."));
		operation.getResponses().addApiResponse("429", error("요청이 너무 많습니다."));
		operation.getResponses().addApiResponse("502", error("외부 기관 요청 또는 응답 처리에 실패했습니다."));
		operation.getResponses().addApiResponse("500", error("서버에서 요청을 처리하지 못했습니다."));
	}

	private void addAccessSecurity(Operation operation, boolean csrfRequired) {
		operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
		SecurityRequirement cookieRequirement = new SecurityRequirement().addList(ACCESS_COOKIE_AUTH);
		if (csrfRequired) {
			cookieRequirement.addList(CSRF_HEADER);
		}
		operation.addSecurityItem(cookieRequirement);
	}

	private ApiResponse error(String description) {
		return new ApiResponse()
			.description(description)
			.content(new Content().addMediaType("application/json", new MediaType()
				.schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))));
	}

	private boolean requiresCsrf(HandlerMethod handlerMethod) {
		if (handlerMethod.hasMethodAnnotation(GetMapping.class)) {
			return false;
		}
		if (handlerMethod.hasMethodAnnotation(PostMapping.class)
			|| handlerMethod.hasMethodAnnotation(PutMapping.class)
			|| handlerMethod.hasMethodAnnotation(PatchMapping.class)
			|| handlerMethod.hasMethodAnnotation(DeleteMapping.class)) {
			return true;
		}
		RequestMapping requestMapping = handlerMethod.getMethodAnnotation(RequestMapping.class);
		return requestMapping != null && List.of(requestMapping.method()).stream()
			.anyMatch(method -> !"GET".equals(method.name())
				&& !"HEAD".equals(method.name())
				&& !"OPTIONS".equals(method.name()));
	}

	private Schema<?> apiErrorResponseSchema() {
		Schema<Object> error = new ObjectSchema()
			.addProperty("code", new StringSchema().example("INVALID_REQUEST"))
			.addProperty("message", new StringSchema().example("요청 형식 또는 값이 올바르지 않습니다."))
			.addProperty("details", new ObjectSchema())
			.addProperty("traceId", new StringSchema().example("7b3f6d8e-1d2c-4f5a-9b0c-123456789abc"));
		error.required(List.of("code", "message", "details", "traceId"));
		Schema<Object> response = new ObjectSchema()
			.addProperty("error", error);
		response.required(List.of("error"));
		return response;
	}
}
