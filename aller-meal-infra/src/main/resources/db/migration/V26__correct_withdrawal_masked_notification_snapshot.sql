UPDATE users account
SET withdrawal_masked_notification_count = (
    SELECT COUNT(*)
    FROM notification_requests request
    WHERE request.user_id = account.user_id
      AND request.failure_code = 'PERSONAL_DATA_MASKED'
      AND request.updated_at >= account.withdrawal_requested_at
)
WHERE account.status = 'WITHDRAWAL_PENDING';
