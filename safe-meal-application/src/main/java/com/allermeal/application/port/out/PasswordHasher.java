package com.allermeal.application.port.out;

public interface PasswordHasher {

	String hash(String password);

	boolean matches(String password, String passwordHash);
}
