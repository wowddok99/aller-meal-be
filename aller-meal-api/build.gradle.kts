plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencies {
	implementation(project(":aller-meal-application"))
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework:spring-tx")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
	runtimeOnly(project(":aller-meal-infra"))
}
