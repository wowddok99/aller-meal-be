package com.allermeal.application.port.out;

public interface PasswordHasher {

	String hash(String password);
}
