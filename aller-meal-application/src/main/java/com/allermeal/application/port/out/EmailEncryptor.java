package com.allermeal.application.port.out;

public interface EmailEncryptor {

	String encrypt(String normalizedEmail);
}
