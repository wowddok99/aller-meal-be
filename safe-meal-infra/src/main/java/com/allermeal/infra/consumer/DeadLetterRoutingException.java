package com.allermeal.infra.consumer;

final class DeadLetterRoutingException extends RuntimeException {

	DeadLetterRoutingException(String message) {
		super(message);
	}
}
