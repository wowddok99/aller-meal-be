# Aller Meal 운영 Runbook

이 문서는 Release 1 운영자가 Compose 기반 배포를 실행하고, 백업·복구·장애
대응을 수행하기 위한 절차입니다. 운영 명령은 저장소 루트에서 실행합니다.

## 1. 운영 전제

- 운영 서버에는 Docker 및 Docker Compose가 설치되어 있어야 합니다.
- 공개 inbound는 Caddy의 80/443만 허용합니다.
- API, Batch, Worker, PostgreSQL, Redis, RabbitMQ, MinIO, Mailpit은 Docker
  network 내부 통신만 사용합니다.
- DNS의 `ALLER_MEAL_DOMAIN`은 Caddy 호스트를 바라보게 설정합니다.
- 운영 비밀값은 `.env.operations.example`을 복사한 `.env.operations`에 주입하거나
  배포 환경의 Secret 관리 기능으로 주입합니다.
- `.env.operations`와 백업 산출물은 Git에 포함하지 않습니다.

## 2. 실행 절차

운영 환경 파일을 준비합니다.

```powershell
cp .env.operations.example .env.operations
```

`.env.operations`에서 아래 값을 운영 값으로 교체합니다.

- `POSTGRES_PASSWORD`
- `RABBITMQ_DEFAULT_PASS`
- `MINIO_ROOT_PASSWORD`
- `AUTH_EMAIL_ENCRYPTION_KEY`
- `AUTH_ACCESS_TOKEN_SIGNING_KEY`
- `AUTH_ALLOWED_ORIGINS`
- `ALLER_MEAL_DOMAIN`
- `EMAIL_VERIFICATION_BASE_URL`
- `PASSWORD_RESET_BASE_URL`
- `ADMIN_BOOTSTRAP_EMAIL`
- `ADMIN_BOOTSTRAP_PASSWORD`
- `NEIS_API_KEY`

운영 Compose 설정을 검증합니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml config
```

서비스를 빌드하고 기동합니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml up -d --build
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml ps
```

공개 프록시 smoke는 Caddy가 공개하는 `/api/*` 경로만 확인합니다.

```powershell
curl -f https://$env:ALLER_MEAL_DOMAIN/api/v1/allergens
```

로컬 smoke 환경에서는 `.env.operations.example`의 18443 포트를 사용할 수 있습니다.

```powershell
curl -k https://localhost:18443/api/v1/allergens
```

readiness는 공개 Caddy route가 아니라 API 컨테이너 내부에서 확인합니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-api `
  wget -q -O - http://localhost:8080/actuator/health/readiness
```

## 3. 비밀값 운영

- 운영 비밀값은 `.env.operations.example`의 기본값을 그대로 사용하지 않습니다.
- `AUTH_EMAIL_ENCRYPTION_KEY`와 `AUTH_ACCESS_TOKEN_SIGNING_KEY`는 최소 32바이트
  이상의 난수 값을 Base64로 인코딩해 사용합니다.
- `AUTH_EMAIL_ENCRYPTION_KEY_VERSION`은 키 교체 시 증가시키며, 기존 암호화 데이터
  복호화 전략이 준비되기 전에는 기존 키를 폐기하지 않습니다.
- 관리자 bootstrap 계정은 최초 관리자 생성 후 제거하거나 빈 값으로 되돌립니다.
- 백업 파일, shell history, 로그에 비밀값이 남지 않도록 파일 권한과 실행 계정을
  제한합니다.

예시 키 생성:

```powershell
openssl rand -base64 32
```

## 4. 백업 절차

백업은 PostgreSQL 논리 백업, RabbitMQ 정의, MinIO 원본 객체를 분리해 보관합니다.
Redis는 캐시와 단기 토큰 저장소이므로 Release 1에서는 영구 복구 대상이 아닙니다.

백업 디렉터리를 준비합니다.

```powershell
New-Item -ItemType Directory -Force backups
```

PostgreSQL 논리 백업을 생성합니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-postgres `
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --file=/tmp/aller-meal.dump'
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml cp `
  aller-meal-postgres:/tmp/aller-meal.dump backups/aller-meal-postgres.dump
```

RabbitMQ topology와 사용자 정의를 백업합니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-rabbitmq `
  rabbitmqctl export_definitions /tmp/rabbitmq-definitions.json
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml cp `
  aller-meal-rabbitmq:/tmp/rabbitmq-definitions.json backups/rabbitmq-definitions.json
```

MinIO 원본 객체를 외부 경로로 동기화합니다. `<compose-project>_default`는 운영
Compose project의 Docker network 이름으로 바꿉니다. 기본 project 이름을 쓰는
경우 저장소 디렉터리명 기준의 network가 생성됩니다.

```powershell
docker run --rm --env-file .env.operations --network <compose-project>_default -v "$PWD/backups:/backup" `
  --entrypoint sh minio/mc:latest `
  -c 'mc alias set allermeal http://aller-meal-minio:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD && mc mirror --overwrite allermeal/aller-meal-raw /backup/minio-raw'
```

백업 직후 산출물을 읽기 전용 저장소로 이동하고 보존 정책을 적용합니다.

## 5. 복구 절차

복구 전 현재 장애 원인과 복구 지점을 기록합니다. 같은 운영 volume에 직접 덮어쓰기
전에 새 서버 또는 새 Compose project에서 복구 rehearsal을 먼저 수행합니다.

서비스를 중지합니다. volume 삭제는 이 runbook의 기본 복구 절차에 포함하지 않습니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml stop `
  aller-meal-api aller-meal-batch aller-meal-worker aller-meal-caddy
```

PostgreSQL 백업을 복원합니다. 대상 DB가 비어 있는 새 PostgreSQL이어야 합니다.
`pg_restore --clean --if-exists`는 대상 DB의 기존 schema와 데이터를 삭제할 수
있습니다. 기존 운영 DB에서 직접 실행하지 말고 새 서버 또는 새 Compose project에서
복구 rehearsal을 완료한 뒤 승인된 복구 창에만 실행합니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml cp `
  backups/aller-meal-postgres.dump aller-meal-postgres:/tmp/aller-meal.dump
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-postgres `
  sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists /tmp/aller-meal.dump'
```

RabbitMQ 정의를 복원합니다. 이 명령은 대상 RabbitMQ의 사용자, 권한, exchange,
queue, binding 정의를 변경할 수 있으므로 새 환경 rehearsal 후 운영 변경 창에만
실행합니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml cp `
  backups/rabbitmq-definitions.json aller-meal-rabbitmq:/tmp/rabbitmq-definitions.json
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-rabbitmq `
  rabbitmqctl import_definitions /tmp/rabbitmq-definitions.json
```

MinIO 원본 객체를 복원합니다. `mc mirror --overwrite`는 같은 object key의 기존
객체를 백업 내용으로 덮어쓸 수 있으므로 복구 대상 bucket과 백업 시점을 확인한 뒤
실행합니다.

```powershell
docker run --rm --env-file .env.operations --network <compose-project>_default -v "$PWD/backups:/backup" `
  --entrypoint sh minio/mc:latest `
  -c 'mc alias set allermeal http://aller-meal-minio:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD && mc mirror --overwrite /backup/minio-raw allermeal/aller-meal-raw'
```

서비스를 다시 시작하고 health를 확인합니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml up -d
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml ps
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-api `
  wget -q -O - http://localhost:8080/actuator/health/readiness
curl -f https://$env:ALLER_MEAL_DOMAIN/api/v1/allergens
```

## 6. 장애 복구

### PostgreSQL 장애

1. `docker compose ... ps`와 PostgreSQL 로그로 health와 재시작 반복 여부를 확인합니다.
2. 일시 장애이면 PostgreSQL 복구 후 API/Batch/Worker readiness가 `UP`인지 확인합니다.
3. 데이터 손상 또는 volume 장애이면 5장 복구 절차로 새 환경에 복원합니다.

조회 명령:

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml logs --tail=200 aller-meal-postgres
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-postgres `
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select now();"'
```

### Redis 장애

Redis는 cache, rate limit, 일회성 토큰 저장소입니다. Redis 장애 중에도 핵심 데이터는
PostgreSQL에 남지만 로그인 제한, 인증 토큰, 공개 조회 캐시는 유실될 수 있습니다.

1. Redis health와 로그를 확인합니다.
2. Redis 복구 후 API readiness를 확인합니다.
3. 장애 시간대의 이메일 인증·비밀번호 재설정 토큰은 재발급 안내를 합니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml logs --tail=200 aller-meal-redis
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-redis redis-cli ping
```

### RabbitMQ 장애

RabbitMQ 장애 중 발행되지 않은 이벤트는 PostgreSQL Outbox에 남습니다. RabbitMQ 복구 후
Batch의 Outbox Publisher가 다시 발행합니다.

1. RabbitMQ health와 queue 상태를 확인합니다.
2. RabbitMQ 복구 후 `outbox_events`의 `PENDING` 잔량이 감소하는지 확인합니다.
3. Worker 오류로 DLQ에 남은 이벤트는 7장 절차로 관리자 API에서 재처리합니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-rabbitmq rabbitmq-diagnostics -q ping
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-postgres `
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select status, count(*) from outbox_events group by status order by status;"'
```

### MinIO 장애

MinIO 장애 중 NEIS 원본 저장과 데이터 보존 삭제가 실패할 수 있습니다. 정규화 급식
데이터와 작업 상태는 PostgreSQL에 기록됩니다.

1. MinIO health와 로그를 확인합니다.
2. 복구 후 새 수집 작업이 원본 객체를 저장하는지 확인합니다.
3. 원본 객체 손실이 있으면 백업에서 `aller-meal-raw` bucket을 복원합니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml logs --tail=200 aller-meal-minio
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-minio `
  curl -f http://localhost:9000/minio/health/live
```

## 7. DLQ 재처리

DLQ 이벤트는 RabbitMQ `aller-meal.events.dlq` queue와 PostgreSQL
`dead_letter_events` 테이블에 기록됩니다. 운영자는 관리자 API로 조회하고
idempotency key를 붙여 재처리 요청을 보냅니다.

DLQ queue와 저장 테이블을 조회합니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-rabbitmq `
  rabbitmqctl list_queues name messages_ready messages_unacknowledged
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-postgres `
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select status, count(*) from dead_letter_events group by status order by status;"'
```

관리자 쿠키로 DLQ 이벤트를 조회합니다.

```powershell
curl -k -b admin-cookie.txt "https://localhost:18443/api/v1/admin/notification-dlq-events?page=1&pageSize=20"
```

원인을 확인한 뒤 재처리합니다.

```powershell
curl -k -X POST -b admin-cookie.txt `
  -H "Idempotency-Key: <uuid-or-incident-key>" `
  "https://localhost:18443/api/v1/admin/notification-dlq-events/<deadLetterEventId>/reprocess"
```

Release 1의 관리자 재처리는 `dead_letter_events.status`를 `REPROCESSED`로 전환하고
감사 로그를 남기는 운영 추적 절차입니다. 원본 메시지를 RabbitMQ에 자동 재발행하는
고급 replay 기능은 후속 릴리스 범위입니다.

## 8. MinIO 보존 절차

NEIS 원본 응답은 `RAW_PAYLOAD_RETENTION_PERIOD` 기준으로 기본 90일 보존됩니다.
비개인 운영 로그는 `OPERATION_LOG_RETENTION_PERIOD` 기준으로 기본 365일 보존됩니다.
Batch의 `DATA_RETENTION_CLEANUP_SCHEDULER_ENABLED=true` 설정이 보존 작업을 실행합니다.

운영 설정:

```powershell
RAW_PAYLOAD_RETENTION_PERIOD=P90D
RAW_PAYLOAD_RETENTION_BATCH_SIZE=100
OPERATION_LOG_RETENTION_PERIOD=P365D
OPERATION_LOG_RETENTION_BATCH_SIZE=100
DATA_RETENTION_CLEANUP_SCHEDULER_ENABLED=true
DATA_RETENTION_CLEANUP_SCHEDULER_FIXED_DELAY=PT24H
```

보존 작업 로그를 확인합니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml logs --tail=200 aller-meal-batch
```

삭제 대상 원본 metadata를 조회합니다. 조회만 수행하며 객체나 metadata를 삭제하지
않습니다.

```powershell
docker compose --env-file .env.operations -f compose.yml -f compose.operations.yml exec -T aller-meal-postgres `
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select raw_object_id, object_key, expires_at from raw_meal_objects where expires_at <= now() order by expires_at asc limit 20;"'
```

보존 작업은 MinIO 객체 삭제 후 PostgreSQL metadata를 삭제합니다. MinIO 삭제 실패 시
metadata를 보존해 다음 실행에서 재시도합니다.

## 9. 알려진 한계

- Release 1 Compose 운영은 단일 호스트 기준입니다. 무중단 rolling update와
  multi-node 고가용성은 포함하지 않습니다.
- Redis 영구 복구는 Release 1 범위가 아닙니다. Redis 장애 시 토큰과 rate limit
  상태는 재생성 또는 재시도 안내로 처리합니다.
- DLQ 관리자 재처리는 상태 전환과 감사 추적 중심입니다. RabbitMQ 원본 메시지 자동
  replay는 포함하지 않습니다.
- MinIO bucket lifecycle rule 자동 설정은 포함하지 않습니다. 보존 삭제는 Batch
  Scheduler가 수행합니다.
- 백업 암호화, 외부 vault 연동, Secret rotation 자동화는 운영 환경 절차로 분리되어
  있으며 애플리케이션 Release 1 기능에는 포함하지 않습니다.
- OpenTelemetry, metrics backend, alert routing은 후속 관측성 고도화 범위입니다.

## 10. 후속 릴리스 범위

- RabbitMQ DLQ 메시지 선택 replay 및 재발행 이력 상세 추적
- PostgreSQL PITR, 백업 암호화, 백업 무결성 자동 검증
- Docker Secret 또는 외부 Secret Manager 기반 배포 표준화
- MinIO bucket lifecycle rule과 object lock 정책 자동화
- Redis Sentinel 또는 managed Redis 기반 고가용성 구성
- Prometheus/Grafana 대시보드와 알림 rule
- Blue/green 또는 rolling 배포 절차
