package com.allermeal.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.allermeal")
public class SafeMealBatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(SafeMealBatchApplication.class, args);
	}
}
