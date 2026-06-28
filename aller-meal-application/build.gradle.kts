plugins {
	`java-library`
}

dependencies {
	api(project(":aller-meal-domain"))
	implementation("org.springframework:spring-tx:7.0.7")
}
