plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencies {
	implementation(project(":safe-meal-application"))
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	runtimeOnly(project(":safe-meal-infra"))
}
