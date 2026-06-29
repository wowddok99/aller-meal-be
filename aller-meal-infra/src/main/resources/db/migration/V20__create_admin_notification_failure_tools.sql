create table dead_letter_events (
    dead_letter_event_id uuid primary key,
    message_id varchar(100) not null,
    event_type varchar(100) not null,
    payload text not null,
    retry_count integer not null,
    status varchar(30) not null,
    reprocessed_by_user_id uuid references users (user_id),
    reprocessed_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    check (retry_count >= 0),
    check (status in ('PENDING', 'REPROCESSED')),
    check (
        (status = 'PENDING' and reprocessed_by_user_id is null and reprocessed_at is null)
        or (status = 'REPROCESSED' and reprocessed_by_user_id is not null and reprocessed_at is not null)
    ),
    check (updated_at >= created_at),
    unique (message_id, event_type)
);

create index idx_dead_letter_events_created_at on dead_letter_events(created_at desc);
create index idx_dead_letter_events_status_created_at on dead_letter_events(status, created_at desc);

create table admin_notification_reprocess_requests (
    reprocess_request_id uuid primary key,
    idempotency_key varchar(200) not null unique,
    actor_user_id uuid not null references users (user_id),
    dead_letter_event_id uuid not null references dead_letter_events (dead_letter_event_id),
    created_at timestamptz not null
);

create index idx_admin_notification_reprocess_requests_created_at
    on admin_notification_reprocess_requests(created_at desc);
create index idx_admin_notification_reprocess_requests_dead_letter_event
    on admin_notification_reprocess_requests(dead_letter_event_id);

create index idx_notification_requests_failed_updated_at
    on notification_requests(status, updated_at desc, notification_id desc)
    where status = 'FAILED';
