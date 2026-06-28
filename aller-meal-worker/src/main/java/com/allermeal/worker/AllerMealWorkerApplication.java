package com.allermeal.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.allermeal")
public class AllerMealWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AllerMealWorkerApplication.class, args);
	}
}
