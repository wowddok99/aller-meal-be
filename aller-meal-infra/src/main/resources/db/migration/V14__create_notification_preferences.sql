CREATE TABLE notification_preferences (
    child_id UUID PRIMARY KEY REFERENCES child_profiles(child_id) ON DELETE CASCADE,
    email_enabled BOOLEAN NOT NULL,
    notification_time TIME NOT NULL,
    timezone VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_notification_preferences_timezone CHECK (timezone = 'Asia/Seoul'),
    CONSTRAINT ck_notification_preferences_updated_at CHECK (updated_at >= created_at)
);

CREATE INDEX ix_notification_preferences_enabled_time
    ON notification_preferences (notification_time)
    WHERE email_enabled;
