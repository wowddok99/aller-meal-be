create table admin_audit_logs (
    audit_log_id uuid primary key,
    actor_user_id uuid,
    target_user_id uuid not null,
    action varchar(80) not null,
    outcome varchar(40) not null,
    detail text,
    created_at timestamptz not null,
    check (action ~ '^[A-Z0-9_]{3,80}$'),
    check (outcome ~ '^[A-Z0-9_]{2,40}$')
);

create index idx_admin_audit_logs_created_at on admin_audit_logs(created_at desc);
create index idx_admin_audit_logs_target_user_id on admin_audit_logs(target_user_id);
