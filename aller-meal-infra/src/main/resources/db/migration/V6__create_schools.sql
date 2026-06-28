CREATE TABLE schools (
    school_id UUID PRIMARY KEY,
    neis_school_code VARCHAR(20) NOT NULL UNIQUE,
    education_office_code VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(500) NOT NULL,
    region VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_schools_name ON schools (name);
