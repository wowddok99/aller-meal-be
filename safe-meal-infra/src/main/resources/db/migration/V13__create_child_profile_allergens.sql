CREATE TABLE child_profile_allergens (
    child_id UUID NOT NULL REFERENCES child_profiles(child_id) ON DELETE CASCADE,
    allergen_code SMALLINT NOT NULL REFERENCES allergens(allergen_code),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (child_id, allergen_code)
);
