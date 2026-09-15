ALTER TABLE users
    DROP CONSTRAINT users_status_check,
    DROP CONSTRAINT ck_users_withdrawal_fields;

ALTER TABLE users
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0
        CONSTRAINT ck_users_session_version CHECK (session_version >= 0),
    ADD CONSTRAINT ck_users_status CHECK (
        status IN ('ACTIVE', 'WITHDRAWAL_PENDING', 'SUSPENDED', 'DISABLED')
    ),
    ADD CONSTRAINT ck_users_withdrawal_fields CHECK (
        (
            status IN ('ACTIVE', 'SUSPENDED')
            AND withdrawal_requested_at IS NULL
            AND withdrawal_due_at IS NULL
            AND personal_data_deleted_at IS NULL
        )
        OR (
            status = 'WITHDRAWAL_PENDING'
            AND withdrawal_requested_at IS NOT NULL
            AND withdrawal_due_at IS NOT NULL
            AND withdrawal_due_at > withdrawal_requested_at
            AND personal_data_deleted_at IS NULL
        )
        OR (
            status = 'DISABLED'
            AND (
                (withdrawal_requested_at IS NULL AND withdrawal_due_at IS NULL)
                OR (withdrawal_requested_at IS NOT NULL AND withdrawal_due_at IS NOT NULL)
            )
            AND (
                personal_data_deleted_at IS NULL
                OR (withdrawal_due_at IS NOT NULL AND personal_data_deleted_at >= withdrawal_due_at)
            )
        )
    );

CREATE INDEX idx_users_admin_manageable_created_at
    ON users(created_at DESC, user_id DESC)
    WHERE status IN ('ACTIVE', 'WITHDRAWAL_PENDING', 'SUSPENDED');
