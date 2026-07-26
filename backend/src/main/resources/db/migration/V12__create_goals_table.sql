CREATE TABLE goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES budget_categories(id) ON DELETE CASCADE,
    amount NUMERIC(10, 2) NOT NULL,
    day_of_month INT NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
    rollover_type TEXT NOT NULL CHECK (rollover_type IN ('REFILL', 'ACCUMULATE')))