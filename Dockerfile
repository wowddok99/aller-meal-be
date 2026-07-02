FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

ARG MODULE

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY aller-meal-domain aller-meal-domain
COPY aller-meal-application aller-meal-application
COPY aller-meal-infra aller-meal-infra
COPY aller-meal-api aller-meal-api
COPY aller-meal-batch aller-meal-batch
COPY aller-meal-worker aller-meal-worker

RUN chmod +x gradlew && ./gradlew ":${MODULE}:bootJar" --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

ARG MODULE

COPY --from=build "/workspace/${MODULE}/build/libs/${MODULE}-0.0.1-SNAPSHOT.jar" app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
