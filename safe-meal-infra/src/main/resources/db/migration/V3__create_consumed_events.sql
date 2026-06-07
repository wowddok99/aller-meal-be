create table consumed_events (
    consumer_name varchar(100) not null,
    event_id uuid not null,
    event_type varchar(100) not null,
    processed_at timestamptz not null,
    primary key (consumer_name, event_id)
);
