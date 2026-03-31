-- month must be the first day of the month; DATE_TRUNC returns timestamp so cast to DATE
CREATE TABLE budget_allocations (
  id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
  category_id UUID           NOT NULL REFERENCES budget_categories(id),
  month       DATE           NOT NULL CHECK (month = DATE_TRUNC('month', month)::DATE),
  assigned    NUMERIC(15, 2) NOT NULL DEFAULT 0,
  UNIQUE (category_id, month)
);

CREATE INDEX idx_alloc_category_month ON budget_allocations(category_id, month);
