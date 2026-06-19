package com.allermeal.api.auth;

import com.allermeal.application.auth.AuthenticationResult;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public final class AuthCookieWriter {

	public static final String ACCESS_TOKEN_COOKIE = "access_token";
	public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

	private final boolean secure;
	private final String sameSite;
	private final Duration accessTokenTtl;
	private final Duration refreshTokenTtl;

	public AuthCookieWriter(
		@Value("${safe-meal.auth.cookie-secure:false}") boolean secure,
		@Value("${safe-meal.auth.cookie-same-site:Lax}") String sameSite,
		@Value("${safe-meal.auth.access-token-ttl:15m}") Duration accessTokenTtl,
		@Value("${safe-meal.auth.refresh-token-ttl:14d}") Duration refreshTokenTtl
	) {
		this.secure = secure;
		this.sameSite = sameSite;
		this.accessTokenTtl = accessTokenTtl;
		this.refreshTokenTtl = refreshTokenTtl;
	}

	public void writeAuthenticationCookies(AuthenticationResult result, HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(
			ACCESS_TOKEN_COOKIE,
			result.accessToken(),
			accessTokenTtl).toString());
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(
			REFRESH_TOKEN_COOKIE,
			result.refreshToken(),
			refreshTokenTtl).toString());
	}

	public void clearAuthenticationCookies(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_TOKEN_COOKIE, "", Duration.ZERO).toString());
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_TOKEN_COOKIE, "", Duration.ZERO).toString());
	}

	private ResponseCookie cookie(String name, String value, Duration maxAge) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(secure)
			.sameSite(sameSite)
			.path("/")
			.maxAge(maxAge)
			.build();
	}
}
