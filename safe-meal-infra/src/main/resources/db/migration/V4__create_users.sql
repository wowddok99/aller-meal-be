create table users (
    user_id uuid primary key,
    encrypted_email text not null check (
        encrypted_email ~ '^v1:[A-Za-z0-9._-]+:[A-Za-z0-9+/]+={0,2}:[A-Za-z0-9+/]+={0,2}$'
    ),
    email_search_hash varchar(64) not null unique,
    password_hash text not null check (btrim(password_hash) <> ''),
    role varchar(20) not null check (role in ('MEMBER', 'ADMIN')),
    status varchar(30) not null check (status in ('ACTIVE', 'WITHDRAWAL_PENDING', 'DISABLED')),
    email_verification_status varchar(20) not null check (email_verification_status in ('UNVERIFIED', 'VERIFIED')),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0 check (version >= 0),
    check (email_search_hash ~ '^[0-9a-f]{64}$'),
    check (updated_at >= created_at)
);
