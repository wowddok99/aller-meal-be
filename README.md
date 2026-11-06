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

로그인 상태 유지 기능의 배포 및 롤백 전제는
[인증 배포 절차](docs/auth-session-deployment.md)를 따릅니다.

## 빌드

테스트를 포함한 빌드에는 Docker CLI와 실행 중인 Docker daemon이 필요합니다.
Redis 통합 테스트는 `redis:8.0.2-alpine` 이미지의 격리 컨테이너를 생성하고
검증 후 제거합니다. 이미지가 없으면 Docker가 내려받으므로 CI에서도 이미지에
접근할 수 있어야 합니다. Docker를 사용할 수 없으면 테스트가 실패합니다.

```powershell
.\gradlew.bat build
```
