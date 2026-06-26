plugins {
	`java-library`
}

dependencies {
	api(project(":safe-meal-domain"))
	implementation("org.springframework:spring-tx:7.0.7")
}
