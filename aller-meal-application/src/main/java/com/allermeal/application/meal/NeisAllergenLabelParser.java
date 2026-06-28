package com.allermeal.application.meal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NeisAllergenLabelParser {

	private static final Pattern ALLERGEN_SUFFIX = Pattern.compile("\\(([^)]*)\\)\\s*$");
	private static final Pattern ALLERGEN_NUMBER = Pattern.compile("\\d+");

	public NeisAllergenLabelParseResult parse(String rawText) {
		if (rawText == null || rawText.isBlank()) {
			return NeisAllergenLabelParseResult.failed();
		}
		Matcher suffix = ALLERGEN_SUFFIX.matcher(rawText.trim());
		if (!suffix.find()) {
			return NeisAllergenLabelParseResult.unknown();
		}
		String label = suffix.group(1).trim();
		if (label.isEmpty()) {
			return NeisAllergenLabelParseResult.failed();
		}
		if (!ALLERGEN_NUMBER.matcher(label).find()) {
			return NeisAllergenLabelParseResult.unknown();
		}
		List<Integer> codes = new ArrayList<>();
		int cursor = 0;
		Matcher numbers = ALLERGEN_NUMBER.matcher(label);
		while (numbers.find()) {
			if (!isSeparatorOnly(label.substring(cursor, numbers.start()))) {
				return NeisAllergenLabelParseResult.failed();
			}
			try {
				int code = Integer.parseInt(numbers.group());
				if (code <= 0) {
					return NeisAllergenLabelParseResult.failed();
				}
				if (!codes.contains(code)) {
					codes.add(code);
				}
			} catch (NumberFormatException exception) {
				return NeisAllergenLabelParseResult.failed();
			}
			cursor = numbers.end();
		}
		if (codes.isEmpty() || !isSeparatorOnly(label.substring(cursor))) {
			return NeisAllergenLabelParseResult.failed();
		}
		return NeisAllergenLabelParseResult.labeled(codes);
	}

	private boolean isSeparatorOnly(String value) {
		return value.chars().allMatch(character -> character == '.' || Character.isWhitespace(character));
	}
}
