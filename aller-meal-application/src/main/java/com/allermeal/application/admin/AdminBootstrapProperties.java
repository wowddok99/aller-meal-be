package com.allermeal.application.admin;

public record AdminBootstrapProperties(
	String email,
	String password
) {

	public boolean enabled() {
		return hasText(email) || hasText(password);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
