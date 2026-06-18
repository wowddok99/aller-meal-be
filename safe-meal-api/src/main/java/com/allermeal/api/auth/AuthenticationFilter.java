package com.allermeal.api.auth;

import com.allermeal.api.error.TraceIdFilter;
import com.allermeal.application.auth.AccessTokenClaims;
import com.allermeal.application.port.out.AccessTokenIssuer;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(TraceIdFilter.ORDER + 1)
public final class AuthenticationFilter extends OncePerRequestFilter {

	public static final String AUTHENTICATED_USER_ATTRIBUTE = AuthenticationFilter.class.getName() + ".user";

	private final AccessTokenIssuer accessTokenIssuer;
	private final UserRepository userRepository;

	public AuthenticationFilter(AccessTokenIssuer accessTokenIssuer, UserRepository userRepository) {
		this.accessTokenIssuer = accessTokenIssuer;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (isPublicRequest(request)) {
			filterChain.doFilter(request, response);
			return;
		}
		if (!request.getRequestURI().startsWith("/api/v1/")) {
			filterChain.doFilter(request, response);
			return;
		}
		Optional<String> token = resolveToken(request);
		if (token.isEmpty()) {
			writeError(response, request, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
			return;
		}
		AccessTokenClaims claims;
		try {
			claims = accessTokenIssuer.verify(token.get());
		} catch (RuntimeException exception) {
			writeError(response, request, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
			return;
		}
		User user = userRepository.findById(claims.userId()).orElse(null);
		if (user == null || user.status() != UserStatus.ACTIVE) {
			writeError(response, request, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
			return;
		}
		if (user.emailVerificationStatus() != EmailVerificationStatus.VERIFIED) {
			writeError(response, request, HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "이메일 인증 후 이용해 주세요.");
			return;
		}
		request.setAttribute(AUTHENTICATED_USER_ATTRIBUTE, user);
		filterChain.doFilter(request, response);
	}

	private boolean isPublicRequest(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.equals("/api/v1/allergens")
			|| path.startsWith("/api/v1/public/")
			|| path.equals("/api/v1/auth/signup")
			|| path.equals("/api/v1/auth/login")
			|| path.equals("/api/v1/auth/email-verifications")
			|| path.equals("/api/v1/auth/email-verifications/confirm");
	}

	private Optional<String> resolveToken(HttpServletRequest request) {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization != null && authorization.startsWith("Bearer ")) {
			return Optional.of(authorization.substring("Bearer ".length()).trim())
				.filter(value -> !value.isBlank());
		}
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
			.filter(cookie -> AuthCookieWriter.ACCESS_TOKEN_COOKIE.equals(cookie.getName()))
			.map(Cookie::getValue)
			.filter(value -> value != null && !value.isBlank())
			.findFirst();
	}

	private void writeError(
		HttpServletResponse response,
		HttpServletRequest request,
		HttpStatus status,
		String code,
		String message
	) throws IOException {
		response.setStatus(status.value());
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("""
			{"error":{"code":"%s","message":"%s","details":{},"traceId":"%s"}}\
			""".formatted(code, message, traceId(request)));
	}

	private String traceId(HttpServletRequest request) {
		Object traceId = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
		if (traceId instanceof String value) {
			return value;
		}
		return "";
	}
}
