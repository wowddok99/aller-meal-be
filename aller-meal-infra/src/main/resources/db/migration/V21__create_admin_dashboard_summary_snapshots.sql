create table admin_dashboard_summary_snapshots (
    summary_snapshot_id uuid primary key,
    generated_at timestamptz not null,
    collection_pending_count bigint not null,
    collection_running_count bigint not null,
    collection_succeeded_count bigint not null,
    collection_failed_count bigint not null,
    labeling_pending_count bigint not null,
    labeling_labeled_count bigint not null,
    labeling_unknown_count bigint not null,
    labeling_failed_count bigint not null,
    outbox_pending_count bigint not null,
    outbox_published_count bigint not null,
    dlq_pending_count bigint not null,
    dlq_reprocessed_count bigint not null,
    notification_pending_count bigint not null,
    notification_sending_count bigint not null,
    notification_retry_pending_count bigint not null,
    notification_sent_count bigint not null,
    notification_failed_count bigint not null,
    notification_canceled_count bigint not null,
    created_at timestamptz not null default current_timestamp,
    check (generated_at <= created_at + interval '5 minutes'),
    check (
        collection_pending_count >= 0
        and collection_running_count >= 0
        and collection_succeeded_count >= 0
        and collection_failed_count >= 0
        and labeling_pending_count >= 0
        and labeling_labeled_count >= 0
        and labeling_unknown_count >= 0
        and labeling_failed_count >= 0
        and outbox_pending_count >= 0
        and outbox_published_count >= 0
        and dlq_pending_count >= 0
        and dlq_reprocessed_count >= 0
        and notification_pending_count >= 0
        and notification_sending_count >= 0
        and notification_retry_pending_count >= 0
        and notification_sent_count >= 0
        and notification_failed_count >= 0
        and notification_canceled_count >= 0
    )
);

create index idx_admin_dashboard_summary_snapshots_generated_at
    on admin_dashboard_summary_snapshots(generated_at desc);
create index idx_meal_items_labeling_status_updated_at
    on meal_items(labeling_status, updated_at desc);
create index idx_outbox_events_status_updated_at
    on outbox_events(status, updated_at desc);
create index idx_notification_requests_status_updated_at
    on notification_requests(status, updated_at desc);
