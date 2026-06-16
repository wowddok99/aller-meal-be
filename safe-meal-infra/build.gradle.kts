plugins {
	`java-library`
}

dependencies {
	implementation(project(":safe-meal-application"))
	implementation(project(":safe-meal-domain"))
	implementation("org.springframework.boot:spring-boot-starter-jdbc:4.0.6")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa:4.0.6")
	implementation("org.springframework.boot:spring-boot-starter-data-redis:4.0.6")
	implementation("org.springframework.boot:spring-boot-starter-mail:4.0.6")
	implementation("org.springframework.boot:spring-boot-flyway:4.0.6")
	implementation("org.springframework.boot:spring-boot-starter-amqp:4.0.6")
	implementation("org.flywaydb:flyway-core:11.14.1")
	implementation("org.flywaydb:flyway-database-postgresql:11.14.1")
	implementation("tools.jackson.core:jackson-databind:3.1.2")
	implementation("io.minio:minio:9.0.1")
	implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.3.0")
	implementation("io.github.resilience4j:resilience4j-retry:2.3.0")
	runtimeOnly("org.postgresql:postgresql:42.7.10")
}
