package com.allermeal.api.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class TraceIdFilter extends OncePerRequestFilter {

	public static final String TRACE_ID_HEADER = "X-Trace-Id";
	public static final String TRACE_ID_ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";

	private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		String traceId = resolveTraceId(request.getHeader(TRACE_ID_HEADER));
		request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
		response.setHeader(TRACE_ID_HEADER, traceId);
		filterChain.doFilter(request, response);
	}

	private String resolveTraceId(String requestedTraceId) {
		if (requestedTraceId != null && VALID_TRACE_ID.matcher(requestedTraceId).matches()) {
			return requestedTraceId;
		}
		return UUID.randomUUID().toString();
	}
}
