CREATE TABLE notification_requests (
    notification_id UUID PRIMARY KEY,
    notification_target_id UUID NOT NULL REFERENCES notification_targets(notification_target_id) ON DELETE CASCADE,
    child_id UUID NOT NULL REFERENCES child_profiles(child_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    notification_date DATE NOT NULL,
    channel VARCHAR(30) NOT NULL,
    reason VARCHAR(30) NOT NULL,
    dedup_key VARCHAR(64) NOT NULL UNIQUE,
    correction_key VARCHAR(64) NOT NULL,
    content_version VARCHAR(64) NOT NULL,
    is_correction BOOLEAN NOT NULL,
    supersedes_notification_id UUID REFERENCES notification_requests(notification_id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count SMALLINT NOT NULL,
    max_attempts SMALLINT NOT NULL,
    next_attempt_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    failure_code VARCHAR(100),
    failure_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_notification_requests_channel CHECK (channel IN ('EMAIL')),
    CONSTRAINT ck_notification_requests_reason CHECK (
        reason IN ('RISK_DETECTED', 'NO_RISK', 'RISK_UNKNOWN', 'RISK_LABELING_FAILED', 'RISK_PENDING', 'NO_MEAL')
    ),
    CONSTRAINT ck_notification_requests_status CHECK (
        status IN ('PENDING', 'SENDING', 'RETRY_PENDING', 'SENT', 'FAILED', 'CANCELED')
    ),
    CONSTRAINT ck_notification_requests_keys CHECK (
        dedup_key ~ '^[0-9a-f]{64}$'
        AND correction_key ~ '^[0-9a-f]{64}$'
        AND content_version ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_notification_requests_attempts CHECK (
        attempt_count >= 0 AND max_attempts BETWEEN 1 AND 10 AND attempt_count <= max_attempts
    ),
    CONSTRAINT ck_notification_requests_correction CHECK (
        (is_correction AND supersedes_notification_id IS NOT NULL)
        OR (NOT is_correction AND supersedes_notification_id IS NULL)
    ),
    CONSTRAINT ck_notification_requests_status_fields CHECK (
        (status = 'PENDING'
            AND attempt_count = 0
            AND next_attempt_at IS NOT NULL
            AND sent_at IS NULL
            AND failure_code IS NULL
            AND failure_message IS NULL)
        OR (status = 'SENDING'
            AND attempt_count >= 1
            AND next_attempt_at IS NULL
            AND sent_at IS NULL
            AND failure_code IS NULL
            AND failure_message IS NULL)
        OR (status = 'RETRY_PENDING'
            AND attempt_count >= 1
            AND next_attempt_at IS NOT NULL
            AND sent_at IS NULL
            AND failure_code IS NOT NULL)
        OR (status = 'SENT'
            AND attempt_count >= 1
            AND next_attempt_at IS NULL
            AND sent_at IS NOT NULL
            AND failure_code IS NULL
            AND failure_message IS NULL)
        OR (status IN ('FAILED', 'CANCELED')
            AND next_attempt_at IS NULL
            AND sent_at IS NULL
            AND failure_code IS NOT NULL)
    ),
    CONSTRAINT ck_notification_requests_failure_code_length CHECK (
        failure_code IS NULL OR char_length(btrim(failure_code)) BETWEEN 1 AND 100
    ),
    CONSTRAINT ck_notification_requests_failure_message_length CHECK (
        failure_message IS NULL OR char_length(btrim(failure_message)) BETWEEN 1 AND 1000
    ),
    CONSTRAINT ck_notification_requests_updated_at CHECK (updated_at >= created_at)
);

CREATE INDEX ix_notification_requests_send_queue
    ON notification_requests (next_attempt_at, notification_id)
    WHERE status IN ('PENDING', 'RETRY_PENDING');

CREATE INDEX ix_notification_requests_correction_key
    ON notification_requests (correction_key, updated_at DESC, notification_id);

CREATE INDEX ix_notification_requests_target_id
    ON notification_requests (notification_target_id);
