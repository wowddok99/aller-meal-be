CREATE INDEX idx_collection_jobs_updated_at_id
    ON collection_jobs (updated_at DESC, collection_job_id DESC);

CREATE INDEX idx_meal_items_updated_at_id
    ON meal_items (updated_at DESC, meal_item_id DESC);

CREATE INDEX idx_outbox_events_updated_at_id
    ON outbox_events (updated_at DESC, event_id DESC);

CREATE INDEX idx_dead_letter_events_updated_at_id
    ON dead_letter_events (updated_at DESC, dead_letter_event_id DESC);

CREATE INDEX idx_notification_requests_updated_at_id
    ON notification_requests (updated_at DESC, notification_id DESC);

CREATE INDEX idx_external_api_logs_created_at_id
    ON external_api_logs (created_at DESC, external_api_log_id DESC);
