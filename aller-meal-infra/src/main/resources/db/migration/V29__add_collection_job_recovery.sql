alter table admin_recollection_requests
    add column request_type varchar(20) not null default 'RECOLLECTION',
    add constraint ck_admin_collection_request_type check (
        (request_type = 'RECOLLECTION' and original_collection_job_id <> collection_job_id)
        or (request_type = 'EXECUTION' and original_collection_job_id = collection_job_id)
    );

create index idx_admin_collection_recovery_lookup
    on admin_recollection_requests(original_collection_job_id, created_at desc, recollection_request_id desc)
    where request_type = 'RECOLLECTION';
create index idx_admin_collection_execution_lookup
    on admin_recollection_requests(original_collection_job_id, created_at desc)
    where request_type = 'EXECUTION';

-- UNION deduplicates visited root/node pairs, including malformed cyclic history.
create view collection_job_recovery_summaries as
with recursive descendants(root_id, node_id) as (
    select original_collection_job_id, collection_job_id
    from admin_recollection_requests where request_type = 'RECOLLECTION'
    union
    select d.root_id, r.collection_job_id
    from descendants d join admin_recollection_requests r on r.original_collection_job_id = d.node_id
    where r.request_type = 'RECOLLECTION' and r.collection_job_id <> d.root_id
), latest as (
    select distinct on (d.root_id) d.root_id, r.collection_job_id, r.created_at, r.recollection_request_id
    from descendants d join admin_recollection_requests r on r.collection_job_id = d.node_id
    where r.request_type = 'RECOLLECTION'
      and (r.original_collection_job_id = d.root_id or exists (
          select 1 from descendants parent where parent.root_id = d.root_id and parent.node_id = r.original_collection_job_id
      ))
    order by d.root_id, r.created_at desc, r.recollection_request_id desc
), resolved as (
    select distinct on (d.root_id) d.root_id, j.collection_job_id, j.updated_at
    from descendants d join collection_jobs j on j.collection_job_id = d.node_id
    where j.status = 'SUCCEEDED' and d.root_id <> d.node_id
    order by d.root_id, j.updated_at desc, j.collection_job_id desc
), active as (
    select d.root_id, bool_or(j.status in ('PENDING', 'RUNNING')) as has_active
    from descendants d join collection_jobs j on j.collection_job_id = d.node_id
    where d.root_id <> d.node_id group by d.root_id
), executions as (
    select original_collection_job_id, max(created_at) as latest_execution_at
    from admin_recollection_requests where request_type = 'EXECUTION' group by original_collection_job_id
)
select j.collection_job_id,
       j.status = 'FAILED' and resolved.collection_job_id is null as unresolved_failure,
       case when resolved.collection_job_id is not null then 'SUCCEEDED'
            when coalesce(active.has_active, false) then 'IN_PROGRESS'
            when latest.collection_job_id is not null then 'FAILED' else 'NOT_REQUESTED' end as recovery_status,
       coalesce(active.has_active, false) as has_active,
       latest.collection_job_id as latest_collection_job_id, target.status as latest_status,
       latest.created_at as requested_at,
       case when target.status in ('SUCCEEDED', 'FAILED') then target.updated_at end as completed_at,
       resolved.collection_job_id as resolved_collection_job_id, resolved.updated_at as resolved_at,
       executions.latest_execution_at
from collection_jobs j
left join latest on latest.root_id = j.collection_job_id
left join collection_jobs target on target.collection_job_id = latest.collection_job_id
left join resolved on resolved.root_id = j.collection_job_id
left join active on active.root_id = j.collection_job_id
left join executions on executions.original_collection_job_id = j.collection_job_id;

-- Only derived snapshots are discarded; collection and request history is retained.
delete from admin_dashboard_summary_snapshots;
alter table admin_dashboard_summary_snapshots
    add column collection_unresolved_failed_count bigint not null check (collection_unresolved_failed_count >= 0);
