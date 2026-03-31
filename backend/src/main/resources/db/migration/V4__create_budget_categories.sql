CREATE TABLE budget_categories (
  id         UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id   UUID    NOT NULL REFERENCES category_groups(id),
  name       TEXT    NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0
);

-- Deferred FK: transactions.category_id -> budget_categories.id
ALTER TABLE transactions
  ADD CONSTRAINT fk_txn_category FOREIGN KEY (category_id) REFERENCES budget_categories(id);
