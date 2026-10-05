-- 원본 RUNNING 이력은 보존하고 관리자 액션에서 사용할 연결 실행 lease를 집합 조회합니다.
create view collection_job_recovery_active_leases as
with recursive descendants(root_id, node_id) as (
    select original_collection_job_id, collection_job_id
    from admin_recollection_requests where request_type = 'RECOLLECTION'
    union
    select d.root_id, r.collection_job_id
    from descendants d join admin_recollection_requests r on r.original_collection_job_id = d.node_id
    where r.request_type = 'RECOLLECTION' and r.collection_job_id <> d.root_id
)
select d.root_id as collection_job_id,
       bool_or(j.status = 'PENDING') as has_pending,
       max(case when j.status = 'RUNNING' then j.lease_until end) as latest_running_lease_until
from descendants d join collection_jobs j on j.collection_job_id = d.node_id
where d.root_id <> d.node_id group by d.root_id;
