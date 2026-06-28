plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencies {
	implementation(project(":aller-meal-application"))
	implementation(project(":aller-meal-infra"))
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-amqp")
	implementation("tools.jackson.core:jackson-databind:3.1.2")
}
