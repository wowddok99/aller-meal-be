package com.allermeal.api.meal.response;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record MealOriginResponse(
	List<String> ingredients,
	String origin
) {

	private static final Pattern BREAK_TAG = Pattern.compile("(?i)<br\\s*/?>");
	private static final Pattern SIMPLE_ORIGIN = Pattern.compile("(?:국내산|수입산|외국산|[가-힣]+산)(?:\\([^()]+\\))?");

	public MealOriginResponse {
		ingredients = List.copyOf(ingredients);
	}

	public static List<MealOriginResponse> parseAllOrNull(String originInfo) {
		if (originInfo == null || originInfo.isBlank()) {
			return List.of();
		}

		List<MealOriginResponse> origins = new ArrayList<>();
		String normalized = BREAK_TAG.matcher(originInfo).replaceAll("\n");
		for (String phrase : normalized.split("[\\r\\n,]", -1)) {
			String trimmed = phrase.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			MealOriginResponse origin = parsePhrase(trimmed);
			if (origin == null) {
				return null;
			}
			origins.add(origin);
		}
		return List.copyOf(origins);
	}

	private static MealOriginResponse parsePhrase(String phrase) {
		int separator = phrase.indexOf(':');
		if (separator >= 0) {
			if (separator != phrase.lastIndexOf(':')) {
				return null;
			}
			return create(phrase.substring(0, separator), phrase.substring(separator + 1));
		}

		int originStart = phrase.lastIndexOf(' ');
		if (originStart < 1) {
			return null;
		}
		String origin = phrase.substring(originStart + 1).trim();
		if (!SIMPLE_ORIGIN.matcher(origin).matches()) {
			return null;
		}
		return create(phrase.substring(0, originStart), origin);
	}

	private static MealOriginResponse create(String rawIngredients, String rawOrigin) {
		String origin = rawOrigin.trim();
		if (origin.isEmpty()) {
			return null;
		}
		List<String> ingredients = new ArrayList<>();
		for (String rawIngredient : rawIngredients.split("·", -1)) {
			String ingredient = rawIngredient.trim();
			if (ingredient.isEmpty()) {
				return null;
			}
			ingredients.add(ingredient);
		}
		return ingredients.isEmpty() ? null : new MealOriginResponse(ingredients, origin);
	}
}
