create table outbox_events (
    event_id uuid primary key,
    event_type varchar(100) not null,
    payload jsonb not null,
    status varchar(20) not null check (status in ('PENDING', 'PUBLISHED')),
    occurred_at timestamptz not null,
    published_at timestamptz,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    check (
        (status = 'PENDING' and published_at is null)
        or (status = 'PUBLISHED' and published_at is not null)
    )
);

create index outbox_events_pending_idx
    on outbox_events (occurred_at, event_id)
    where status = 'PENDING';

create trigger outbox_events_updated_at
before update on outbox_events
for each row execute function set_updated_at();
