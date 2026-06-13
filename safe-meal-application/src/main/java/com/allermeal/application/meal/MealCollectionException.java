package com.allermeal.application.meal;

public class MealCollectionException extends RuntimeException {

	private final String code;

	public MealCollectionException(String code, String message) {
		super(message);
		this.code = code;
	}

	public MealCollectionException(String code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public static MealCollectionException normalized(String code, String message, Throwable cause) {
		return new MealCollectionException(normalizeCode(code), normalizeMessage(message), cause);
	}

	public String code() {
		return code;
	}

	private static String normalizeCode(String value) {
		String normalized = value == null ? "COLLECTION_FAILED" : value.trim().replaceAll("[^A-Z0-9_]", "_");
		return normalized.isEmpty() ? "COLLECTION_FAILED" : normalized.substring(0, Math.min(normalized.length(), 100));
	}

	private static String normalizeMessage(String value) {
		String normalized = value == null ? "급식 수집에 실패했습니다." : value.trim().replaceAll("\\s+", " ");
		return normalized.isEmpty() ? "급식 수집에 실패했습니다."
			: normalized.substring(0, Math.min(normalized.length(), 1000));
	}
}
