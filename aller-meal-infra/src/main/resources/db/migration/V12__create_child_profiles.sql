CREATE TABLE child_profiles (
    child_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    grade SMALLINT NOT NULL CHECK (grade BETWEEN 1 AND 12),
    class_number SMALLINT NOT NULL CHECK (class_number BETWEEN 1 AND 99),
    school_id UUID NOT NULL REFERENCES schools(school_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (updated_at >= created_at)
);

CREATE INDEX ix_child_profiles_user_id ON child_profiles (user_id);
