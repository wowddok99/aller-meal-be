package com.allermeal.application.auth;

public record SignupCommand(String email, String password, String displayName, String usageType) {
	public SignupCommand(String email, String password) { this(email, password, null, null); }
	@Override public String toString() { return "SignupCommand[REDACTED]"; }
}
