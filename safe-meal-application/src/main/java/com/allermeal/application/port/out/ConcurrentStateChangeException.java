package com.allermeal.application.port.out;

public class ConcurrentStateChangeException extends RuntimeException {

	public ConcurrentStateChangeException(String message) {
		super(message);
	}
}
