package com.allermeal.api.error;

import com.allermeal.api.error.response.ApiError;
import com.allermeal.api.error.response.ApiErrorResponse;
import com.allermeal.application.auth.DuplicateEmailException;
import com.allermeal.application.auth.EmailAlreadyVerifiedException;
import com.allermeal.application.auth.InvalidSignupRequestException;
import com.allermeal.application.auth.UserEmailNotFoundException;
import com.allermeal.application.school.InvalidSchoolSearchRequestException;
import com.allermeal.application.school.NeisApiException;
import com.allermeal.application.school.NeisInvalidResponseException;
import com.allermeal.application.school.SchoolNotFoundException;
import com.allermeal.domain.common.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public final class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(DomainException.class)
	ResponseEntity<ApiErrorResponse> handleDomainException(HttpServletRequest request) {
		return response(HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE_ENTITY",
			"요청한 작업을 처리할 수 없습니다.", request);
	}

	@ExceptionHandler(InvalidSchoolSearchRequestException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidSchoolSearchRequest(HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
			"요청 값이 올바르지 않습니다.", request);
	}

	@ExceptionHandler(InvalidSignupRequestException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidSignupRequest(HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
			"요청 값이 올바르지 않습니다.", request);
	}

	@ExceptionHandler(DuplicateEmailException.class)
	ResponseEntity<ApiErrorResponse> handleDuplicateEmail(HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS",
			"이미 가입된 이메일입니다.", request);
	}

	@ExceptionHandler(EmailAlreadyVerifiedException.class)
	ResponseEntity<ApiErrorResponse> handleEmailAlreadyVerified(HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, "EMAIL_ALREADY_VERIFIED",
			"이미 인증된 이메일입니다.", request);
	}

	@ExceptionHandler(UserEmailNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleUserEmailNotFound(HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
			"요청한 이메일 계정을 찾을 수 없습니다.", request);
	}

	@ExceptionHandler(NeisApiException.class)
	ResponseEntity<ApiErrorResponse> handleNeisApi(HttpServletRequest request) {
		return response(HttpStatus.BAD_GATEWAY, "NEIS_API_ERROR",
			"학교 정보 제공 기관 요청에 실패했습니다.", request);
	}

	@ExceptionHandler(NeisInvalidResponseException.class)
	ResponseEntity<ApiErrorResponse> handleNeisInvalidResponse(HttpServletRequest request) {
		return response(HttpStatus.BAD_GATEWAY, "NEIS_INVALID_RESPONSE",
			"학교 정보 제공 기관 응답을 처리할 수 없습니다.", request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiErrorResponse> handleNotFound(HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
			"요청한 리소스를 찾을 수 없습니다.", request);
	}

	@ExceptionHandler(SchoolNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleSchoolNotFound(HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
			"요청한 학교를 찾을 수 없습니다.", request);
	}

	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		HandlerMethodValidationException.class,
		BindException.class,
		MissingServletRequestParameterException.class,
		MethodArgumentTypeMismatchException.class
	})
	ResponseEntity<ApiErrorResponse> handleInvalidRequest(HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
			"요청 값이 올바르지 않습니다.", request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorResponse> handleMalformedRequest(HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
			"요청 본문 형식이 올바르지 않습니다.", request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpServletRequest request) {
		return response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
			"지원하지 않는 요청 방식입니다.", request);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(HttpServletRequest request) {
		return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
			"지원하지 않는 요청 형식입니다.", request);
	}

	@ExceptionHandler(ErrorResponseException.class)
	ResponseEntity<ApiErrorResponse> handleSpringError(ErrorResponseException exception, HttpServletRequest request) {
		HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
		if (status == null || status.is5xxServerError()) {
			return handleUnexpectedException(exception, request);
		}
		return response(status, "INVALID_REQUEST", "요청을 처리할 수 없습니다.", request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
		String traceId = traceId(request);
		log.error("예상하지 못한 API 오류가 발생했습니다. traceId={}", traceId, exception);
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
			"서버에서 요청을 처리하지 못했습니다.", traceId);
	}

	private ResponseEntity<ApiErrorResponse> response(
		HttpStatus status,
		String code,
		String message,
		HttpServletRequest request
	) {
		return response(status, code, message, traceId(request));
	}

	private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message, String traceId) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(
			new ApiError(code, message, Map.of(), traceId)));
	}

	private String traceId(HttpServletRequest request) {
		return (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
	}
}
