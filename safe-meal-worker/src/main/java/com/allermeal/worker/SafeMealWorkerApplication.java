package com.allermeal.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.allermeal")
public class SafeMealWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SafeMealWorkerApplication.class, args);
	}
}
