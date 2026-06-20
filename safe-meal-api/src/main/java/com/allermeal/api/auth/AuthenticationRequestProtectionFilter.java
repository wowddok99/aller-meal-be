package com.allermeal.api.auth;

import com.allermeal.api.error.TraceIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(TraceIdFilter.ORDER + 1)
public final class AuthenticationRequestProtectionFilter extends OncePerRequestFilter {

	private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of("""
		local count = redis.call('INCR', KEYS[1])
		if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end
		return count
		""", Long.class);
	private final StringRedisTemplate redis;
	private final int maxRequests;
	private final Duration window;
	private final Set<String> allowedOrigins;

	public AuthenticationRequestProtectionFilter(
		StringRedisTemplate redis,
		@Value("${safe-meal.auth.rate-limit-max-requests:20}") int maxRequests,
		@Value("${safe-meal.auth.rate-limit-window:1m}") Duration window,
		@Value("${safe-meal.auth.allowed-origins:http://localhost:3000,http://localhost:8080}") String allowedOrigins
	) {
		this.redis = redis;
		this.maxRequests = maxRequests;
		this.window = window;
		this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim)
			.filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		if (request.getRequestURI().startsWith("/api/v1/auth/")) {
			Long count = redis.execute(RATE_LIMIT_SCRIPT,
				List.of("auth-rate-limit:v1:" + request.getRemoteAddr() + ":" + request.getRequestURI()),
				Long.toString(Math.max(1, window.toSeconds())));
			if (count != null && count > maxRequests) { writeError(response, request, 429, "AUTH_RATE_LIMITED", "요청이 너무 많습니다."); return; }
		}
		if (requiresCsrf(request) && hasAuthenticationCookie(request) && !validCsrf(request)) {
			writeError(response, request, 403, "CSRF_VALIDATION_FAILED", "요청을 처리할 수 없습니다."); return;
		}
		chain.doFilter(request, response);
	}

	private boolean requiresCsrf(HttpServletRequest request) {
		return request.getRequestURI().startsWith("/api/v1/")
			&& !HttpMethod.GET.matches(request.getMethod()) && !HttpMethod.HEAD.matches(request.getMethod())
			&& !HttpMethod.OPTIONS.matches(request.getMethod());
	}

	private boolean hasAuthenticationCookie(HttpServletRequest request) {
		return cookie(request, AuthCookieWriter.ACCESS_TOKEN_COOKIE) != null
			|| cookie(request, AuthCookieWriter.REFRESH_TOKEN_COOKIE) != null;
	}

	private boolean validCsrf(HttpServletRequest request) {
		String origin = request.getHeader(HttpHeaders.ORIGIN);
		String cookie = cookie(request, AuthCookieWriter.CSRF_TOKEN_COOKIE);
		String header = request.getHeader("X-CSRF-Token");
		return origin != null && allowedOrigins.contains(origin) && cookie != null && header != null
			&& MessageDigest.isEqual(cookie.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
	}

	private String cookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) return null;
		return Arrays.stream(cookies).filter(cookie -> name.equals(cookie.getName())).map(Cookie::getValue).findFirst().orElse(null);
	}

	private void writeError(HttpServletResponse response, HttpServletRequest request, int status, String code, String message) throws IOException {
		response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
		Object trace = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
		response.getWriter().write("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"details\":{},\"traceId\":\"" + (trace == null ? "" : trace) + "\"}}");
	}
}
