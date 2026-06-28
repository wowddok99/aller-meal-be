CREATE TABLE notification_targets (
    notification_target_id UUID PRIMARY KEY,
    child_id UUID NOT NULL REFERENCES child_profiles(child_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    school_id UUID NOT NULL REFERENCES schools(school_id),
    notification_date DATE NOT NULL,
    notification_time TIME NOT NULL,
    timezone VARCHAR(50) NOT NULL,
    reason VARCHAR(30) NOT NULL,
    risk_level VARCHAR(30),
    risk_version VARCHAR(64),
    meal_count SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_notification_targets_child_date UNIQUE (child_id, notification_date),
    CONSTRAINT ck_notification_targets_timezone CHECK (timezone = 'Asia/Seoul'),
    CONSTRAINT ck_notification_targets_reason CHECK (
        reason IN ('RISK_DETECTED', 'NO_RISK', 'RISK_UNKNOWN', 'RISK_LABELING_FAILED', 'RISK_PENDING', 'NO_MEAL')
    ),
    CONSTRAINT ck_notification_targets_risk_level CHECK (
        risk_level IS NULL OR risk_level IN ('RISKY', 'SAFE', 'UNKNOWN', 'LABELING_FAILED', 'PENDING')
    ),
    CONSTRAINT ck_notification_targets_risk_version CHECK (
        risk_version IS NULL OR risk_version ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_notification_targets_meal_count_nonnegative CHECK (meal_count >= 0),
    CONSTRAINT ck_notification_targets_no_meal CHECK (
        (reason = 'NO_MEAL' AND meal_count = 0 AND risk_version IS NULL AND risk_level IS NULL)
        OR (reason <> 'NO_MEAL' AND risk_level IS NOT NULL)
    ),
    CONSTRAINT ck_notification_targets_updated_at CHECK (updated_at >= created_at)
);

CREATE INDEX ix_notification_targets_date_reason
    ON notification_targets (notification_date, reason);

CREATE INDEX ix_notification_targets_user_id
    ON notification_targets (user_id, notification_date);
