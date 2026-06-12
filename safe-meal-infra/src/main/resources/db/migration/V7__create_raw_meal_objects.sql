CREATE TABLE raw_meal_objects (
    raw_object_id UUID PRIMARY KEY,
    object_key VARCHAR(500) NOT NULL UNIQUE,
    sha256_hash CHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_raw_meal_objects_size_nonnegative CHECK (size_bytes >= 0),
    CONSTRAINT ck_raw_meal_objects_expiry_after_received CHECK (expires_at > received_at)
);

CREATE INDEX ix_raw_meal_objects_expires_at ON raw_meal_objects (expires_at);
