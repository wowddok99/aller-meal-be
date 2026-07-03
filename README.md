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

```powershell
docker compose up -d
docker compose ps
```

PostgreSQL, Redis, RabbitMQ, MinIO, Mailpit이 로컬 인터페이스에 기동됩니다.
기본 계정 정보를 변경하려면 `.env.example`을 `.env`로 복사한 뒤 값을 수정합니다.
로컬 포트 충돌이 있으면 `POSTGRES_PUBLISHED_PORT`, `REDIS_PUBLISHED_PORT`,
`RABBITMQ_AMQP_PUBLISHED_PORT`, `MINIO_API_PUBLISHED_PORT`,
`MAILPIT_SMTP_PUBLISHED_PORT` 값을 바꿉니다.

## 운영 Compose와 Caddy

운영 overlay는 Caddy만 80/443 포트를 공개하고, API/Batch/Worker 및 PostgreSQL,
Redis, RabbitMQ, MinIO, Mailpit은 Docker network 내부 통신만 사용합니다.
`.env.operations.example`을 `.env.operations`로 복사한 뒤 도메인과 비밀값을
운영 환경에 맞게 수정합니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml up -d --build
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml ps
```

`ALLER_MEAL_DOMAIN`은 단일 공개 도메인으로 설정합니다. 운영 환경에서는
`CADDY_HTTP_PORT=80`, `CADDY_HTTPS_PORT=443`을 사용합니다. 로컬 예시는 기존
개발 서버와 충돌하지 않도록 18080/18443을 사용합니다. Caddy는 해당 도메인의
`/api/*` 요청을 `aller-meal-api:8080`으로 전달하고, 그 외 경로는 404로 응답합니다.
공개 운영 환경에서는 DNS가 Caddy 호스트를 바라보게 하고 80/443 inbound만 열어
HTTPS 인증서가 발급되도록 합니다.

인증 쿠키는 HTTPS 전제를 기준으로 `ALLER_MEAL_AUTH_COOKIE_SECURE=true`,
`ALLER_MEAL_AUTH_COOKIE_SAME_SITE=Lax`를 사용합니다. 같은 도메인의 프론트엔드에서
쿠키 인증 요청을 보낼 수 있도록 `AUTH_ALLOWED_ORIGINS=https://<도메인>`으로
맞춥니다.

프록시 수동 확인:

```powershell
curl -k https://localhost:18443/api/v1/allergens
curl -k https://localhost:18443/not-found
```

실행, 백업, 복구, 비밀값, DLQ 재처리, MinIO 보존 절차는
[운영 Runbook](docs/operations-runbook.md)을 따릅니다.

## 빌드

```powershell
.\gradlew.bat build
```
