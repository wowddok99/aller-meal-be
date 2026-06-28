ALTER TABLE collection_jobs DROP CONSTRAINT ck_collection_jobs_raw_object_by_status;
ALTER TABLE collection_jobs ADD CONSTRAINT ck_collection_jobs_raw_object_by_status CHECK (
    raw_object_id IS NULL OR status IN ('SUCCEEDED', 'FAILED')
);
