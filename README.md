# Aller Meal Backend

학교 급식 정보를 수집하고, 알레르기 정보를 바탕으로 사용자에게 주의가 필요한
식단을 안내하는 Spring Boot 기반의 백엔드 서비스입니다.

## 구성

- `aller-meal-api`: HTTP API 애플리케이션
- `aller-meal-batch`: 스케줄 및 배치 애플리케이션
- `aller-meal-worker`: 비동기 Worker 애플리케이션
- `aller-meal-domain`: 프레임워크 독립 도메인
- `aller-meal-application`: 유스케이스 및 포트
- `aller-meal-infra`: PostgreSQL, RabbitMQ 등 외부 시스템 어댑터

## 요구사항

- Java 21
- Docker 및 Docker Compose

## 로컬 인프라 실행

로컬 개발은 Docker로 인프라만 실행하고, `aller-meal-api`, `aller-meal-batch`,
`aller-meal-worker`는 IntelliJ에서 직접 실행합니다.

```powershell
cp .env.example .env
docker compose up -d
docker compose ps
```

PostgreSQL, Redis, RabbitMQ, MinIO, Mailpit이 로컬 인터페이스에 기동됩니다.
로컬 포트 충돌이 있으면 `POSTGRES_PUBLISHED_PORT`, `REDIS_PUBLISHED_PORT`,
`RABBITMQ_AMQP_PUBLISHED_PORT`, `MINIO_API_PUBLISHED_PORT`,
`MAILPIT_SMTP_PUBLISHED_PORT` 값을 바꿉니다.

환경변수 기준과 IntelliJ 실행 설정은 `.codex/docs/environment-variables.md`를
따릅니다.

## 빌드

```powershell
.\gradlew.bat build
```
