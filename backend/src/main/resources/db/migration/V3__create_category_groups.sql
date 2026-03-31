CREATE TABLE category_groups (
  id         UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
  name       TEXT    NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0
);
