package com.allermeal.application.port.out;

public interface LoginAttemptStore {

	boolean isLocked(String emailHash);

	boolean recordFailure(String emailHash);

	void clear(String emailHash);

	boolean clearIfUnlocked(String emailHash);
}
