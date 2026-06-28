plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

dependencies {
	implementation(project(":aller-meal-application"))
	implementation("org.springframework.boot:spring-boot-starter")
	runtimeOnly(project(":aller-meal-infra"))
}
