package com.allermeal.api.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allermeal.api.error.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class ApiExceptionHandlerTest {

	@Test
	void mapsInvalidAdminUserRoleChangeToConflictCode() {
		ApiExceptionHandler handler = new ApiExceptionHandler();
		HttpServletRequest request = requestWithTraceId("trace-test");

		ResponseEntity<ApiErrorResponse> response =
			handler.handleInvalidAdminUserRoleChange(request);

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertEquals("INVALID_ADMIN_ROLE_CHANGE", response.getBody().error().code());
		assertEquals("관리자 권한으로 변경할 수 없는 사용자입니다.", response.getBody().error().message());
		assertEquals("trace-test", response.getBody().error().traceId());
	}

	private static HttpServletRequest requestWithTraceId(String traceId) {
		return (HttpServletRequest) Proxy.newProxyInstance(
			HttpServletRequest.class.getClassLoader(),
			new Class<?>[] { HttpServletRequest.class },
			(proxy, method, args) -> {
				if (method.getName().equals("getAttribute")
					&& TraceIdFilter.TRACE_ID_ATTRIBUTE.equals(args[0])) {
					return traceId;
				}
				return null;
			});
	}
}
