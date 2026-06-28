package com.allermeal.domain.common;

import java.util.Map;
import java.util.Objects;

public abstract class DomainException extends RuntimeException {

	private final String code;
	private final Map<String, Object> details;

	protected DomainException(String code, String message, Map<String, Object> details) {
		super(Objects.requireNonNull(message, "message는 null일 수 없습니다."));
		this.code = Objects.requireNonNull(code, "code는 null일 수 없습니다.");
		this.details = Map.copyOf(Objects.requireNonNull(details, "details는 null일 수 없습니다."));
	}

	public String code() {
		return code;
	}

	public Map<String, Object> details() {
		return details;
	}
}
