package com.allermeal.api.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class ApiErrorController implements ErrorController {

	private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);

	@RequestMapping("/error")
	ResponseEntity<ApiErrorResponse> error(HttpServletRequest request, HttpServletResponse response) {
		String traceId = traceId(request);
		response.setHeader(TraceIdFilter.TRACE_ID_HEADER, traceId);
		HttpStatus status = status(request);
		if (status == null) {
			return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
				"요청한 리소스를 찾을 수 없습니다.", traceId);
		}

		if (status.is5xxServerError()) {
			Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
			if (exception instanceof Throwable throwable) {
				log.error("API 오류 fallback이 처리되었습니다. traceId={}, status={}", traceId, status.value(), throwable);
			} else {
				log.error("API 오류 fallback이 처리되었습니다. traceId={}, status={}", traceId, status.value());
			}
			return response(status, "INTERNAL_SERVER_ERROR", "서버에서 요청을 처리하지 못했습니다.", traceId);
		}
		if (status == HttpStatus.NOT_FOUND) {
			return response(status, "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.", traceId);
		}
		if (status == HttpStatus.METHOD_NOT_ALLOWED) {
			return response(status, "METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.", traceId);
		}
		if (status == HttpStatus.UNSUPPORTED_MEDIA_TYPE) {
			return response(status, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 형식입니다.", traceId);
		}
		if (status == HttpStatus.BAD_REQUEST) {
			return response(status, "MALFORMED_REQUEST", "요청 형식이 올바르지 않습니다.", traceId);
		}
		return response(status, "INVALID_REQUEST", "요청을 처리할 수 없습니다.", traceId);
	}

	private HttpStatus status(HttpServletRequest request) {
		Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		if (value instanceof Integer statusCode) {
			return HttpStatus.resolve(statusCode);
		}
		return null;
	}

	private String traceId(HttpServletRequest request) {
		Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
		return value instanceof String traceId ? traceId : UUID.randomUUID().toString();
	}

	private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message, String traceId) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(
			new ApiErrorResponse.ApiError(code, message, Map.of(), traceId)));
	}
}
