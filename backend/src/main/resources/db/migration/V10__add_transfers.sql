ALTER TABLE transactions
ADD COLUMN transfer_id UUID REFERENCES transactions(id) ON DELETE SET NULL;
CREATE INDEX idx_transactions_transfer_id ON transactions(transfer_id);