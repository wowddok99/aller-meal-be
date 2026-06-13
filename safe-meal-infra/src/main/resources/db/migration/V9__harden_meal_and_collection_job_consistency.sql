ALTER TABLE meals ADD COLUMN source_version VARCHAR(200);
ALTER TABLE meals ADD COLUMN source_received_at TIMESTAMPTZ;

UPDATE meals
SET source_version = 'legacy:' || meal_id,
    source_received_at = updated_at
WHERE source_version IS NULL OR source_received_at IS NULL;

ALTER TABLE meals ALTER COLUMN source_version SET NOT NULL;
ALTER TABLE meals ALTER COLUMN source_received_at SET NOT NULL;

ALTER TABLE collection_jobs ADD CONSTRAINT ck_collection_jobs_response_time_by_status CHECK (
    (status IN ('SUCCEEDED', 'FAILED') AND response_time_millis IS NOT NULL)
    OR (status IN ('PENDING', 'RUNNING') AND response_time_millis IS NULL)
);
ALTER TABLE collection_jobs ADD CONSTRAINT ck_collection_jobs_failure_code_length CHECK (
    failure_code IS NULL OR (char_length(btrim(failure_code)) BETWEEN 1 AND 100)
);
ALTER TABLE collection_jobs ADD CONSTRAINT ck_collection_jobs_failure_message_length CHECK (
    failure_message IS NULL OR (char_length(btrim(failure_message)) BETWEEN 1 AND 1000)
);

ALTER TABLE meals ADD CONSTRAINT ck_meals_source_version_length CHECK (
    char_length(btrim(source_version)) BETWEEN 1 AND 200
);
ALTER TABLE meals ADD CONSTRAINT ck_meals_nutrition_info_length CHECK (
    nutrition_info IS NULL OR (char_length(btrim(nutrition_info)) BETWEEN 1 AND 20000)
);
ALTER TABLE meals ADD CONSTRAINT ck_meals_origin_info_length CHECK (
    origin_info IS NULL OR (char_length(btrim(origin_info)) BETWEEN 1 AND 20000)
);
ALTER TABLE meal_items ADD CONSTRAINT ck_meal_items_name_length CHECK (
    char_length(btrim(name)) BETWEEN 1 AND 300
);
ALTER TABLE meal_items ADD CONSTRAINT ck_meal_items_raw_text_length CHECK (
    char_length(btrim(raw_text)) BETWEEN 1 AND 1000
);
