CREATE TABLE school_collection_subscriptions (
    school_id UUID PRIMARY KEY REFERENCES schools(school_id),
    registered_child_count INTEGER NOT NULL CHECK (registered_child_count >= 0),
    grace_ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_school_collection_subscriptions_state CHECK (
        (registered_child_count > 0 AND grace_ends_at IS NULL)
        OR (registered_child_count = 0 AND grace_ends_at IS NOT NULL)
    ),
    CONSTRAINT ck_school_collection_subscriptions_updated_at CHECK (updated_at >= created_at)
);

INSERT INTO school_collection_subscriptions (
    school_id, registered_child_count, grace_ends_at, created_at, updated_at
)
SELECT school_id, COUNT(*), NULL, MIN(created_at), MAX(updated_at)
FROM child_profiles
GROUP BY school_id;

CREATE INDEX ix_school_collection_subscriptions_grace_ends_at
    ON school_collection_subscriptions (grace_ends_at);
