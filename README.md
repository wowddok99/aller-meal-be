# Aller Meal Backend

학교 급식 정보를 수집하고, 알레르기 정보를 바탕으로 사용자에게 주의가 필요한
식단을 안내하는 Spring Boot 기반의 백엔드 서비스입니다.

## 구성

- `safe-meal-api`: HTTP API 애플리케이션
- `safe-meal-batch`: 스케줄 및 배치 애플리케이션
- `safe-meal-worker`: 비동기 Worker 애플리케이션
- `safe-meal-domain`: 프레임워크 독립 도메인
- `safe-meal-application`: 유스케이스 및 포트
- `safe-meal-infra`: PostgreSQL, RabbitMQ 등 외부 시스템 어댑터

## 요구사항

- Java 21
- Docker 및 Docker Compose

## 로컬 인프라 실행

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

PostgreSQL, Redis, RabbitMQ, MinIO, Mailpit이 로컬 인터페이스에 기동됩니다.

## 빌드

```powershell
.\gradlew.bat build
```
