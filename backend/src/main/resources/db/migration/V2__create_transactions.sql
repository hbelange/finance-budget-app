-- category_id FK to budget_categories is deferred to V4 (table does not exist yet)
CREATE TABLE transactions (
  id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id  UUID           NOT NULL REFERENCES accounts(id),
  date        DATE           NOT NULL,
  payee       TEXT,
  category_id UUID,
  amount      NUMERIC(15, 2) NOT NULL,
  memo        TEXT,
  cleared     BOOLEAN        NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_txn_account       ON transactions(account_id);
CREATE INDEX idx_txn_category_date ON transactions(category_id, date);
