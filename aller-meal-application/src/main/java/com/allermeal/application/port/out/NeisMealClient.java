package com.allermeal.application.port.out;

import com.allermeal.application.port.out.result.RawMealResponse;
import com.allermeal.domain.school.School;
import com.allermeal.domain.meal.MealType;
import java.time.LocalDate;

public interface NeisMealClient {

	/**
	 * CircuitBreaker permission을 획득해 외부 응답을 가져온다. 성공 반환 후 호출자는 아래
	 * 결과 기록 메서드 중 정확히 하나를 호출해야 한다.
	 */
	RawMealResponse fetch(School school, LocalDate mealDate, MealType mealType);

	/**
	 * 외부 호출은 성공했지만 내부 실패로 응답 validation을 수행하지 못한 경우 기록한다.
	 */
	void recordExternalCallSuccess();

	void recordValidationSuccess();

	void recordValidationFailure(RuntimeException exception);
}
