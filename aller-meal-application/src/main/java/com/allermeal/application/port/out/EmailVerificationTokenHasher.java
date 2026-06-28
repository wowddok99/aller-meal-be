package com.allermeal.application.port.out;

public interface EmailVerificationTokenHasher {

	String hash(String token);
}
