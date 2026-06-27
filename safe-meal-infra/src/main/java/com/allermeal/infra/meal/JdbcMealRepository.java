package com.allermeal.infra.meal;

import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.result.MealSaveResult;
import com.allermeal.application.port.out.result.MealQueryResult;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.meal.MealId;
import com.allermeal.domain.meal.MealItem;
import com.allermeal.domain.meal.MealItemId;
import com.allermeal.domain.meal.MealItemLabelingStatus;
import com.allermeal.domain.meal.MealLabelingStatus;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.school.SchoolId;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcMealRepository implements MealRepository {

	private static final String UPSERT_MEAL_SQL = """
		INSERT INTO meals (
		    meal_id, school_id, meal_date, meal_type, source_version, source_received_at,
		    labeling_status, nutrition_info, origin_info, created_at, updated_at
		)
		VALUES (
		    :mealId, :schoolId, :mealDate, :mealType, :sourceVersion, :sourceReceivedAt,
		    :labelingStatus, :nutritionInfo, :originInfo,
		    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
		)
		ON CONFLICT (school_id, meal_date, meal_type) DO UPDATE SET
		    source_version = EXCLUDED.source_version,
		    source_received_at = EXCLUDED.source_received_at,
		    labeling_status = EXCLUDED.labeling_status,
		    nutrition_info = EXCLUDED.nutrition_info,
		    origin_info = EXCLUDED.origin_info,
		    updated_at = CURRENT_TIMESTAMP
		WHERE meals.source_received_at < EXCLUDED.source_received_at
		  AND meals.source_version <> EXCLUDED.source_version
		RETURNING meal_id
		""";

	private final JdbcClient jdbcClient;

	public JdbcMealRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	@Transactional
	public MealSaveResult save(Meal meal) {
		Optional<UUID> persistedMealId = jdbcClient.sql(UPSERT_MEAL_SQL)
			.param("mealId", meal.id().value())
			.param("schoolId", meal.schoolId().value())
			.param("mealDate", meal.mealDate())
			.param("mealType", meal.mealType().name())
			.param("sourceVersion", meal.sourceVersion())
			.param("sourceReceivedAt", OffsetDateTime.ofInstant(meal.sourceReceivedAt(), ZoneOffset.UTC))
			.param("labelingStatus", meal.labelingStatus().name())
			.param("nutritionInfo", meal.nutritionInfo())
			.param("originInfo", meal.originInfo())
			.query(UUID.class)
			.optional();

		if (persistedMealId.isEmpty()) {
			Meal persistedMeal = findByNaturalKey(meal.schoolId(), meal.mealDate(), meal.mealType()).orElseThrow();
			return new MealSaveResult(persistedMeal, false);
		}

		UUID mealId = persistedMealId.get();
		jdbcClient.sql("""
				DELETE FROM meal_item_allergens
				WHERE meal_item_id IN (SELECT meal_item_id FROM meal_items WHERE meal_id = :mealId)
				""")
			.param("mealId", mealId)
			.update();
		jdbcClient.sql("DELETE FROM meal_items WHERE meal_id = :mealId")
			.param("mealId", mealId)
			.update();
		for (MealItem item : meal.items()) {
			insertItem(mealId, item);
		}
		Meal persistedMeal = findByNaturalKey(meal.schoolId(), meal.mealDate(), meal.mealType()).orElseThrow();
		return new MealSaveResult(persistedMeal, true);
	}

	@Override
	public Optional<Meal> findById(MealId mealId) {
		return jdbcClient.sql("""
				SELECT meal_id, school_id, meal_date, meal_type, source_version, source_received_at,
				       labeling_status, nutrition_info, origin_info
				FROM meals
				WHERE meal_id = :mealId
				""")
			.param("mealId", mealId.value())
			.query((resultSet, rowNum) -> {
				MealId persistedMealId = new MealId(resultSet.getObject("meal_id", UUID.class));
				return new Meal(
					persistedMealId,
					new SchoolId(resultSet.getObject("school_id", UUID.class)),
					resultSet.getObject("meal_date", LocalDate.class),
					MealType.valueOf(resultSet.getString("meal_type")),
					resultSet.getString("source_version"),
					resultSet.getObject("source_received_at", OffsetDateTime.class).toInstant(),
					MealLabelingStatus.valueOf(resultSet.getString("labeling_status")),
					resultSet.getString("nutrition_info"),
					resultSet.getString("origin_info"),
					findItems(persistedMealId));
			})
			.optional();
	}

	@Override
	public Optional<Meal> findByNaturalKey(SchoolId schoolId, LocalDate mealDate, MealType mealType) {
		return jdbcClient.sql("""
				SELECT meal_id, school_id, meal_date, meal_type, source_version, source_received_at,
				       labeling_status, nutrition_info, origin_info
				FROM meals
				WHERE school_id = :schoolId AND meal_date = :mealDate AND meal_type = :mealType
				""")
			.param("schoolId", schoolId.value())
			.param("mealDate", mealDate)
			.param("mealType", mealType.name())
			.query((resultSet, rowNum) -> {
				MealId mealId = new MealId(resultSet.getObject("meal_id", UUID.class));
				return new Meal(
					mealId,
					new SchoolId(resultSet.getObject("school_id", UUID.class)),
					resultSet.getObject("meal_date", LocalDate.class),
					MealType.valueOf(resultSet.getString("meal_type")),
					resultSet.getString("source_version"),
					resultSet.getObject("source_received_at", OffsetDateTime.class).toInstant(),
					MealLabelingStatus.valueOf(resultSet.getString("labeling_status")),
					resultSet.getString("nutrition_info"),
					resultSet.getString("origin_info"),
					findItems(mealId));
			})
			.optional();
	}

	@Override
	public List<MealQueryResult> findCollectedInRange(SchoolId schoolId, LocalDate startDate, LocalDate endDate) {
		return jdbcClient.sql("""
				SELECT v.meal_date, v.meal_type, v.has_meal,
				       m.meal_id, m.school_id, m.source_version, m.source_received_at,
				       m.labeling_status, m.nutrition_info, m.origin_info
				FROM meal_collection_versions v
				LEFT JOIN meals m
				  ON m.school_id = v.school_id
				 AND m.meal_date = v.meal_date
				 AND m.meal_type = v.meal_type
				 AND v.has_meal = TRUE
				WHERE v.school_id = :schoolId AND v.meal_date BETWEEN :startDate AND :endDate
				ORDER BY v.meal_date, v.meal_type
				""")
			.param("schoolId", schoolId.value())
			.param("startDate", startDate)
			.param("endDate", endDate)
			.query((resultSet, rowNum) -> {
				LocalDate mealDate = resultSet.getObject("meal_date", LocalDate.class);
				MealType mealType = MealType.valueOf(resultSet.getString("meal_type"));
				UUID mealIdValue = resultSet.getObject("meal_id", UUID.class);
				if (mealIdValue == null) {
					return new MealQueryResult(mealDate, mealType, null);
				}
				MealId mealId = new MealId(mealIdValue);
				Meal meal = new Meal(
					mealId,
					new SchoolId(resultSet.getObject("school_id", UUID.class)),
					mealDate,
					mealType,
					resultSet.getString("source_version"),
					resultSet.getObject("source_received_at", OffsetDateTime.class).toInstant(),
					MealLabelingStatus.valueOf(resultSet.getString("labeling_status")),
					resultSet.getString("nutrition_info"),
					resultSet.getString("origin_info"),
					findItems(mealId));
				return new MealQueryResult(mealDate, mealType, meal);
			})
			.list();
	}

	@Override
	public Map<MealItemId, List<Integer>> findAllergenCodesByMealIds(List<MealId> mealIds) {
		Objects.requireNonNull(mealIds, "급식 ID 목록은 null일 수 없습니다.");
		if (mealIds.isEmpty()) {
			return Map.of();
		}
		Map<MealItemId, List<Integer>> allergenCodes = new HashMap<>();
		jdbcClient.sql("""
				SELECT mia.meal_item_id, mia.allergen_code
				FROM meal_item_allergens mia
				JOIN meal_items mi ON mi.meal_item_id = mia.meal_item_id
				WHERE mi.meal_id IN (:mealIds)
				ORDER BY mia.meal_item_id, mia.allergen_code
				""")
			.param("mealIds", mealIds.stream().map(MealId::value).toList())
			.query((resultSet, rowNum) -> Map.entry(
				new MealItemId(resultSet.getObject("meal_item_id", UUID.class)),
				resultSet.getInt("allergen_code")))
			.list()
			.forEach(entry -> allergenCodes.computeIfAbsent(entry.getKey(), ignored -> new java.util.ArrayList<>())
				.add(entry.getValue()));
		return allergenCodes;
	}

	@Override
	@Transactional
	public boolean saveAllergenLabels(Meal meal, Map<MealItemId, List<Integer>> allergenCodesByItemId) {
		Objects.requireNonNull(allergenCodesByItemId, "메뉴 알레르기 라벨은 null일 수 없습니다.");
		int mealRows = jdbcClient.sql("""
				UPDATE meals
				SET labeling_status = :labelingStatus,
				    updated_at = CURRENT_TIMESTAMP
				WHERE meal_id = :mealId AND labeling_status = 'PENDING'
				""")
			.param("mealId", meal.id().value())
			.param("labelingStatus", meal.labelingStatus().name())
			.update();
		if (mealRows == 0) {
			return false;
		}
		if (mealRows != 1) {
			throw new IllegalStateException("급식 라벨링 상태 전이에 실패했습니다.");
		}

		jdbcClient.sql("""
				DELETE FROM meal_item_allergens
				WHERE meal_item_id IN (SELECT meal_item_id FROM meal_items WHERE meal_id = :mealId)
				""")
			.param("mealId", meal.id().value())
			.update();

		for (MealItem item : meal.items()) {
			int itemRows = jdbcClient.sql("""
					UPDATE meal_items
					SET labeling_status = :labelingStatus,
					    updated_at = CURRENT_TIMESTAMP
					WHERE meal_item_id = :itemId AND meal_id = :mealId
					""")
				.param("itemId", item.id().value())
				.param("mealId", meal.id().value())
				.param("labelingStatus", item.labelingStatus().name())
				.update();
			if (itemRows != 1) {
				throw new IllegalStateException("메뉴 라벨링 상태 전이에 실패했습니다.");
			}
			for (Integer allergenCode : allergenCodesByItemId.getOrDefault(item.id(), List.of())) {
				jdbcClient.sql("""
						INSERT INTO meal_item_allergens (meal_item_id, allergen_code, created_at, updated_at)
						VALUES (:itemId, :allergenCode, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
						ON CONFLICT (meal_item_id, allergen_code) DO NOTHING
						""")
					.param("itemId", item.id().value())
					.param("allergenCode", allergenCode)
					.update();
			}
		}
		return true;
	}

	private void insertItem(UUID mealId, MealItem item) {
		jdbcClient.sql("""
				INSERT INTO meal_items (
				    meal_item_id, meal_id, name, raw_text, display_order, labeling_status, created_at, updated_at
				)
				VALUES (
				    :itemId, :mealId, :name, :rawText, :displayOrder, :labelingStatus, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
				)
				""")
			.param("itemId", item.id().value())
			.param("mealId", mealId)
			.param("name", item.name())
			.param("rawText", item.rawText())
			.param("displayOrder", item.displayOrder())
			.param("labelingStatus", item.labelingStatus().name())
			.update();
	}

	private List<MealItem> findItems(MealId mealId) {
		return jdbcClient.sql("""
				SELECT meal_item_id, name, raw_text, display_order, labeling_status
				FROM meal_items
				WHERE meal_id = :mealId
				ORDER BY display_order
				""")
			.param("mealId", mealId.value())
			.query((resultSet, rowNum) -> new MealItem(
				new MealItemId(resultSet.getObject("meal_item_id", UUID.class)),
				resultSet.getString("name"),
				resultSet.getString("raw_text"),
				resultSet.getInt("display_order"),
				MealItemLabelingStatus.valueOf(resultSet.getString("labeling_status"))))
			.list();
	}
}
