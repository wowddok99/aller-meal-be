plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencies {
	implementation(project(":safe-meal-application"))
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework:spring-tx")
	runtimeOnly(project(":safe-meal-infra"))
}
