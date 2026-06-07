plugins {
	`java-library`
}

dependencies {
	implementation(project(":safe-meal-application"))
	implementation(project(":safe-meal-domain"))
	implementation("org.springframework.boot:spring-boot-starter-jdbc:4.0.6")
	implementation("org.springframework.boot:spring-boot-flyway:4.0.6")
	implementation("org.springframework.boot:spring-boot-starter-amqp:4.0.6")
	implementation("org.flywaydb:flyway-core:11.14.1")
	implementation("org.flywaydb:flyway-database-postgresql:11.14.1")
	runtimeOnly("org.postgresql:postgresql:42.7.10")
}
