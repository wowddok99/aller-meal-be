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

## 테스트 규칙

- 모든 Java 모듈은 루트 Gradle 설정의 JUnit 5와 `useJUnitPlatform()`을 사용합니다.
- 단위 테스트는 `src/test/java`에 두고 클래스 이름을 `*Test`로 끝냅니다.
- 통합 테스트는 `src/test/java`에 두고 클래스 이름을 `*IntegrationTest`로 끝냅니다.
- 통합 테스트는 기본 `test` 실행에 포함합니다. 외부 서비스가 필요하면 Docker Compose를
  먼저 기동하고 필요한 환경 조건을 테스트 문서에 명시합니다.
- 테스트 fixture는 한 모듈 내부 테스트에서만 사용합니다. 여러 모듈이 공유해야 하는
  fixture는 테스트 전용 공통 모듈 도입을 별도 검토하며 운영 코드 모듈에 두지 않습니다.
