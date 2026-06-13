package com.allermeal.infra.meal;

import com.allermeal.application.meal.MealCollectionException;
import com.allermeal.application.port.out.NeisMealNormalizer;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.meal.MealId;
import com.allermeal.domain.meal.MealItem;
import com.allermeal.domain.meal.MealItemId;
import com.allermeal.domain.meal.MealItemLabelingStatus;
import com.allermeal.domain.meal.MealLabelingStatus;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.raw.RawObjectMetadata;
import com.allermeal.domain.school.School;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class NeisMealJsonNormalizer implements NeisMealNormalizer {

	private static final String SUCCESS_CODE = "INFO-000";
	private static final String NO_DATA_CODE = "INFO-200";
	private static final DateTimeFormatter NEIS_DATE = DateTimeFormatter.BASIC_ISO_DATE;
	private static final Pattern ALLERGEN_SUFFIX = Pattern.compile("\\s*\\([0-9.]+\\)\\s*$");

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public List<Meal> normalize(
		School school,
		LocalDate mealDate,
		MealType mealType,
		byte[] rawPayload,
		RawObjectMetadata metadata
	) {
		JsonNode root = parse(rawPayload);
		JsonNode directResult = root.get("RESULT");
		if (directResult != null) {
			String code = requiredText(directResult, "CODE");
			if (NO_DATA_CODE.equals(code)) {
				if (root.get("mealServiceDietInfo") != null) {
					throw invalid("무자료 결과와 급식 데이터가 함께 존재합니다.");
				}
				return List.of();
			}
			throw new MealCollectionException("NEIS_API_ERROR", "NEIS 급식 응답 오류입니다.");
		}

		Rows result = rows(root);
		JsonNode rows = result.rows();
		List<Meal> meals = new ArrayList<>();
		for (JsonNode row : rows) {
			validateTarget(row, school, mealDate, mealType);
			List<MealItem> items = menuItems(requiredText(row, "DDISH_NM"));
			String nutritionInfo = optionalText(row, "CAL_INFO");
			String nutrients = optionalText(row, "NTR_INFO");
			if (nutrients != null) {
				nutritionInfo = nutritionInfo == null ? nutrients : nutritionInfo + "\n" + nutrients;
			}
			meals.add(new Meal(
				new MealId(UUID.randomUUID()),
				school.id(),
				mealDate,
				mealType,
				metadata.sha256Hash(),
				metadata.receivedAt(),
				MealLabelingStatus.PENDING,
				normalizeBreaks(nutritionInfo),
				normalizeBreaks(optionalText(row, "ORPLC_INFO")),
				items));
		}
		if (meals.size() > 1) {
			throw invalid("동일 대상 급식 행이 여러 건입니다.");
		}
		if (meals.size() != result.totalCount()) {
			throw invalid("list_total_count와 row 개수가 일치하지 않습니다.");
		}
		return List.copyOf(meals);
	}

	private JsonNode parse(byte[] rawPayload) {
		try {
			JsonNode root = objectMapper.readTree(new String(rawPayload, StandardCharsets.UTF_8));
			if (root == null || !root.isObject()) {
				throw invalid("최상위 구조가 올바르지 않습니다.");
			}
			return root;
		} catch (MealCollectionException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new MealCollectionException("NEIS_INVALID_RESPONSE", "NEIS 급식 응답 JSON이 올바르지 않습니다.", exception);
		}
	}

	private Rows rows(JsonNode root) {
		JsonNode service = root.get("mealServiceDietInfo");
		if (service == null || !service.isArray() || service.size() < 2) {
			throw invalid("mealServiceDietInfo 구조가 없습니다.");
		}
		JsonNode headContainer = service.get(0);
		JsonNode rowContainer = service.get(1);
		if (headContainer == null || !headContainer.isObject() || rowContainer == null || !rowContainer.isObject()) {
			throw invalid("head 또는 row container가 올바르지 않습니다.");
		}
		JsonNode head = headContainer.get("head");
		JsonNode rows = rowContainer.get("row");
		if (head == null || !head.isArray() || rows == null || !rows.isArray()) {
			throw invalid("head 또는 row 구조가 올바르지 않습니다.");
		}
		if (head.size() < 2
			|| head.get(0) == null || !head.get(0).isObject()
			|| head.get(1) == null || !head.get(1).isObject()) {
			throw invalid("head 결과 구조가 올바르지 않습니다.");
		}
		JsonNode result = head.get(1).get("RESULT");
		if (result == null || !SUCCESS_CODE.equals(requiredText(result, "CODE"))) {
			throw invalid("정상 응답 code가 없습니다.");
		}
		JsonNode count = head.get(0).get("list_total_count");
		if (count == null || !count.isInt() || count.intValue() < 1) {
			throw invalid("성공 응답 list_total_count가 올바르지 않습니다.");
		}
		if (rows.isEmpty()) {
			throw invalid("성공 응답 row가 비어 있습니다.");
		}
		return new Rows(rows, count.intValue());
	}

	private void validateTarget(JsonNode row, School school, LocalDate mealDate, MealType mealType) {
		if (!requiredText(row, "ATPT_OFCDC_SC_CODE").equals(school.educationOfficeCode())
			|| !requiredText(row, "SD_SCHUL_CODE").equals(school.neisSchoolCode())
			|| !requiredText(row, "MLSV_YMD").equals(NEIS_DATE.format(mealDate))
			|| !requiredText(row, "MMEAL_SC_CODE").equals(mealTypeCode(mealType))) {
			throw invalid("요청 대상과 응답 대상이 일치하지 않습니다.");
		}
	}

	private List<MealItem> menuItems(String rawMenu) {
		String[] lines = rawMenu.split("(?i)<br\\s*/?>");
		List<MealItem> items = new ArrayList<>();
		for (String line : lines) {
			String rawText = line.trim();
			if (!rawText.isEmpty()) {
				String name = ALLERGEN_SUFFIX.matcher(rawText).replaceFirst("").trim();
				items.add(new MealItem(
					new MealItemId(UUID.randomUUID()),
					name,
					rawText,
					items.size(),
					MealItemLabelingStatus.PENDING));
			}
		}
		if (items.isEmpty()) {
			throw invalid("메뉴 항목이 없습니다.");
		}
		return items;
	}

	private String requiredText(JsonNode node, String fieldName) {
		String value = optionalText(node, fieldName);
		if (value == null) {
			throw invalid("필수 필드가 올바르지 않습니다: " + fieldName);
		}
		return value;
	}

	private String optionalText(JsonNode node, String fieldName) {
		JsonNode value = node == null ? null : node.get(fieldName);
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isString() || value.textValue().isBlank()) {
			throw invalid("문자열 필드가 올바르지 않습니다: " + fieldName);
		}
		return value.textValue().trim();
	}

	private String normalizeBreaks(String value) {
		return value == null ? null : value.replaceAll("(?i)<br\\s*/?>", "\n").trim();
	}

	private String mealTypeCode(MealType mealType) {
		return switch (mealType) {
			case BREAKFAST -> "1";
			case LUNCH -> "2";
			case DINNER -> "3";
		};
	}

	private MealCollectionException invalid(String detail) {
		return new MealCollectionException("NEIS_INVALID_RESPONSE", "NEIS 급식 응답이 올바르지 않습니다: " + detail);
	}

	private record Rows(JsonNode rows, int totalCount) {
	}
}
