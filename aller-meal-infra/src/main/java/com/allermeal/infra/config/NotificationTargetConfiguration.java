package com.allermeal.infra.config;

import com.allermeal.application.notification.NotificationTargetScheduler;
import com.allermeal.application.port.out.ChildAllergenRepository;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.NotificationTargetRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationTargetConfiguration {

	@Bean
	NotificationTargetScheduler notificationTargetScheduler(
		NotificationTargetRepository notificationTargetRepository,
		ChildAllergenRepository childAllergenRepository,
		MealRepository mealRepository,
		Clock clock
	) {
		return new NotificationTargetScheduler(
			notificationTargetRepository, childAllergenRepository, mealRepository, clock);
	}
}
