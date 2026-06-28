CREATE TABLE collection_jobs (
    collection_job_id UUID PRIMARY KEY,
    school_id UUID NOT NULL REFERENCES schools (school_id),
    meal_date DATE NOT NULL,
    meal_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_time_millis BIGINT,
    failure_code VARCHAR(100),
    failure_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_collection_jobs_meal_type CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER')),
    CONSTRAINT ck_collection_jobs_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_collection_jobs_response_time_nonnegative CHECK (
        response_time_millis IS NULL OR response_time_millis >= 0
    ),
    CONSTRAINT ck_collection_jobs_failure_info CHECK (
        (status = 'FAILED' AND failure_code IS NOT NULL)
        OR (status <> 'FAILED' AND failure_code IS NULL AND failure_message IS NULL)
    )
);

CREATE UNIQUE INDEX ux_collection_jobs_active_target
    ON collection_jobs (school_id, meal_date, meal_type)
    WHERE status IN ('PENDING', 'RUNNING');
CREATE INDEX ix_collection_jobs_status_created_at ON collection_jobs (status, created_at);

CREATE TABLE meals (
    meal_id UUID PRIMARY KEY,
    school_id UUID NOT NULL REFERENCES schools (school_id),
    meal_date DATE NOT NULL,
    meal_type VARCHAR(20) NOT NULL,
    labeling_status VARCHAR(30) NOT NULL,
    nutrition_info TEXT,
    origin_info TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_meals_natural_key UNIQUE (school_id, meal_date, meal_type),
    CONSTRAINT ck_meals_meal_type CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER')),
    CONSTRAINT ck_meals_labeling_status CHECK (
        labeling_status IN ('PENDING', 'LABELED', 'UNKNOWN', 'LABELING_FAILED')
    )
);

CREATE INDEX ix_meals_school_date ON meals (school_id, meal_date);

CREATE TABLE meal_items (
    meal_item_id UUID PRIMARY KEY,
    meal_id UUID NOT NULL REFERENCES meals (meal_id) ON DELETE CASCADE,
    name VARCHAR(300) NOT NULL,
    raw_text VARCHAR(1000) NOT NULL,
    display_order INTEGER NOT NULL,
    labeling_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_meal_items_order UNIQUE (meal_id, display_order),
    CONSTRAINT ck_meal_items_display_order_nonnegative CHECK (display_order >= 0),
    CONSTRAINT ck_meal_items_labeling_status CHECK (
        labeling_status IN ('PENDING', 'LABELED', 'UNKNOWN', 'LABELING_FAILED')
    )
);

CREATE TABLE meal_item_allergens (
    meal_item_id UUID NOT NULL REFERENCES meal_items (meal_item_id) ON DELETE CASCADE,
    allergen_code SMALLINT NOT NULL REFERENCES allergens (allergen_code),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (meal_item_id, allergen_code)
);
