CREATE TABLE admin_user_access_audit_events (
    event_id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES users(user_id),
    target_user_id UUID NOT NULL REFERENCES users(user_id),
    action VARCHAR(40) NOT NULL,
    before_role VARCHAR(20) NOT NULL,
    after_role VARCHAR(20) NOT NULL,
    before_status VARCHAR(30) NOT NULL,
    after_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_admin_user_access_audit_action CHECK (
        action IN ('PROMOTE_USER_TO_ADMIN', 'SUSPEND_USER', 'UNSUSPEND_USER')
    ),
    CONSTRAINT ck_admin_user_access_audit_before_role CHECK (
        before_role IN ('MEMBER', 'ADMIN')
    ),
    CONSTRAINT ck_admin_user_access_audit_after_role CHECK (
        after_role IN ('MEMBER', 'ADMIN')
    ),
    CONSTRAINT ck_admin_user_access_audit_before_status CHECK (
        before_status IN ('ACTIVE', 'WITHDRAWAL_PENDING', 'SUSPENDED', 'DISABLED')
    ),
    CONSTRAINT ck_admin_user_access_audit_after_status CHECK (
        after_status IN ('ACTIVE', 'WITHDRAWAL_PENDING', 'SUSPENDED', 'DISABLED')
    ),
    CONSTRAINT ck_admin_user_access_audit_reason CHECK (
        length(btrim(reason)) BETWEEN 1 AND 500
    )
);

CREATE INDEX idx_admin_user_access_audit_events_target_created_at
    ON admin_user_access_audit_events(target_user_id, created_at DESC, event_id DESC);
