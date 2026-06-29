create table external_api_logs (
    external_api_log_id uuid primary key,
    provider varchar(40) not null,
    operation varchar(80) not null,
    school_id uuid not null references schools (school_id),
    meal_date date not null,
    meal_type varchar(20) not null,
    method varchar(10) not null,
    endpoint varchar(300) not null,
    http_status integer,
    outcome varchar(40) not null,
    failure_code varchar(100),
    response_time_millis bigint,
    created_at timestamptz not null,
    check (meal_type in ('BREAKFAST', 'LUNCH', 'DINNER')),
    check (http_status is null or (http_status between 100 and 599)),
    check (response_time_millis is null or response_time_millis >= 0),
    check (outcome in ('SUCCEEDED', 'FAILED')),
    check ((outcome = 'FAILED' and failure_code is not null) or (outcome = 'SUCCEEDED' and failure_code is null))
);

create index idx_external_api_logs_created_at on external_api_logs(created_at desc);
create index idx_external_api_logs_provider_created_at on external_api_logs(provider, created_at desc);
create index idx_external_api_logs_target on external_api_logs(school_id, meal_date, meal_type);

create table admin_recollection_requests (
    recollection_request_id uuid primary key,
    idempotency_key varchar(200) not null unique,
    actor_user_id uuid not null references users (user_id),
    original_collection_job_id uuid not null references collection_jobs (collection_job_id),
    collection_job_id uuid not null references collection_jobs (collection_job_id),
    created_at timestamptz not null
);

create index idx_admin_recollection_requests_created_at on admin_recollection_requests(created_at desc);
create index idx_admin_recollection_requests_original_job on admin_recollection_requests(original_collection_job_id);
