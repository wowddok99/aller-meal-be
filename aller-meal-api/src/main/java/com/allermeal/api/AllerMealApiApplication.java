package com.allermeal.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.allermeal")
public class AllerMealApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AllerMealApiApplication.class, args);
	}
}
