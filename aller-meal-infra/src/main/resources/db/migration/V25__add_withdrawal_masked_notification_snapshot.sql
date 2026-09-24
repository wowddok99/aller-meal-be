ALTER TABLE users
    ADD COLUMN withdrawal_masked_notification_count INTEGER;

UPDATE users account
SET withdrawal_masked_notification_count = (
    SELECT COUNT(*)
    FROM notification_requests request
    WHERE request.user_id = account.user_id
      AND request.failure_code = 'PERSONAL_DATA_MASKED'
)
WHERE account.status = 'WITHDRAWAL_PENDING';

ALTER TABLE users
    ADD CONSTRAINT ck_users_withdrawal_masked_notification_count CHECK (
        (
            status = 'WITHDRAWAL_PENDING'
            AND withdrawal_masked_notification_count IS NOT NULL
            AND withdrawal_masked_notification_count >= 0
        )
        OR (
            status <> 'WITHDRAWAL_PENDING'
            AND withdrawal_masked_notification_count IS NULL
        )
    );
