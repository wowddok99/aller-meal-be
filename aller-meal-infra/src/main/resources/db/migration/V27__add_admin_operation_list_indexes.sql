CREATE INDEX idx_collection_jobs_status_updated_at
    ON collection_jobs (status, updated_at DESC, collection_job_id DESC);

CREATE INDEX idx_meal_items_labeling_status_updated_at_id
    ON meal_items (labeling_status, updated_at DESC, meal_item_id DESC);

CREATE INDEX idx_outbox_events_status_updated_at_id
    ON outbox_events (status, updated_at DESC, event_id DESC);

CREATE INDEX idx_dead_letter_events_status_updated_at_id
    ON dead_letter_events (status, updated_at DESC, dead_letter_event_id DESC);

CREATE INDEX idx_notification_requests_status_updated_at_id
    ON notification_requests (status, updated_at DESC, notification_id DESC);
