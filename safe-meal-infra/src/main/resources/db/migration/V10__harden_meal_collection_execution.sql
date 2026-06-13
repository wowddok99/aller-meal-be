ALTER TABLE collection_jobs ADD COLUMN collection_duration_millis BIGINT;
ALTER TABLE collection_jobs ADD COLUMN lease_until TIMESTAMPTZ;
ALTER TABLE collection_jobs ADD COLUMN raw_object_id UUID REFERENCES raw_meal_objects (raw_object_id) ON DELETE SET NULL;

UPDATE collection_jobs
SET collection_duration_millis = response_time_millis
WHERE status IN ('SUCCEEDED', 'FAILED') AND collection_duration_millis IS NULL;

UPDATE collection_jobs
SET status = 'FAILED',
    response_time_millis = 0,
    collection_duration_millis = 0,
    failure_code = 'LEASE_MIGRATED_EXPIRED',
    failure_message = 'lease 도입 전 RUNNING 작업을 만료 처리했습니다.',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'RUNNING';

ALTER TABLE collection_jobs ADD CONSTRAINT ck_collection_jobs_collection_duration_nonnegative CHECK (
    collection_duration_millis IS NULL OR collection_duration_millis >= 0
);
ALTER TABLE collection_jobs ADD CONSTRAINT ck_collection_jobs_collection_duration_by_status CHECK (
    (status IN ('SUCCEEDED', 'FAILED') AND collection_duration_millis IS NOT NULL)
    OR (status IN ('PENDING', 'RUNNING') AND collection_duration_millis IS NULL)
);
ALTER TABLE collection_jobs ADD CONSTRAINT ck_collection_jobs_lease_by_status CHECK (
    (status = 'RUNNING' AND lease_until IS NOT NULL)
    OR (status <> 'RUNNING' AND lease_until IS NULL)
);
ALTER TABLE collection_jobs ADD CONSTRAINT ck_collection_jobs_raw_object_by_status CHECK (
    raw_object_id IS NULL OR status = 'SUCCEEDED'
);

CREATE INDEX ix_collection_jobs_running_lease ON collection_jobs (lease_until)
    WHERE status = 'RUNNING';

CREATE TABLE meal_collection_versions (
    school_id UUID NOT NULL REFERENCES schools (school_id),
    meal_date DATE NOT NULL,
    meal_type VARCHAR(20) NOT NULL,
    source_version VARCHAR(200) NOT NULL,
    source_received_at TIMESTAMPTZ NOT NULL,
    has_meal BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (school_id, meal_date, meal_type),
    CONSTRAINT ck_meal_collection_versions_meal_type CHECK (
        meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER')
    )
);
