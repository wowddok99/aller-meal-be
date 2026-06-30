ALTER TABLE users
    ADD COLUMN withdrawal_requested_at TIMESTAMPTZ,
    ADD COLUMN withdrawal_due_at TIMESTAMPTZ,
    ADD COLUMN personal_data_deleted_at TIMESTAMPTZ;

UPDATE users
SET withdrawal_requested_at = updated_at,
    withdrawal_due_at = updated_at + INTERVAL '7 days'
WHERE status = 'WITHDRAWAL_PENDING'
  AND withdrawal_requested_at IS NULL
  AND withdrawal_due_at IS NULL;

ALTER TABLE users
    ADD CONSTRAINT ck_users_withdrawal_fields CHECK (
        (
            status = 'ACTIVE'
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

CREATE INDEX idx_users_withdrawal_due_at
    ON users(withdrawal_due_at)
    WHERE status = 'WITHDRAWAL_PENDING' AND personal_data_deleted_at IS NULL;
