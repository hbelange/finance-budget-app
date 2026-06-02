ALTER TABLE accounts
  ADD COLUMN cc_payment_category_id UUID REFERENCES budget_categories(id);

-- Create "Credit Card Payments" group for each user that has CC accounts (if not already present)
INSERT INTO category_groups (id, name, sort_order, user_sub)
SELECT gen_random_uuid(),
       'Credit Card Payments',
       COALESCE((SELECT MAX(cg2.sort_order) FROM category_groups cg2 WHERE cg2.user_sub = ua.user_sub), -1) + 1,
       ua.user_sub
FROM (SELECT DISTINCT user_sub FROM accounts WHERE type = 'CREDIT_CARD') ua
WHERE NOT EXISTS (
  SELECT 1 FROM category_groups cg
  WHERE cg.user_sub = ua.user_sub AND cg.name = 'Credit Card Payments'
);

-- Create one budget_category per CC account, named after the account
INSERT INTO budget_categories (id, group_id, name, sort_order)
SELECT gen_random_uuid(), cg.id, a.name, 0
FROM accounts a
JOIN category_groups cg ON cg.user_sub = a.user_sub AND cg.name = 'Credit Card Payments'
WHERE a.type = 'CREDIT_CARD';

-- Point each CC account at its new payment category
UPDATE accounts
SET cc_payment_category_id = (
  SELECT bc.id
  FROM budget_categories bc
  JOIN category_groups cg ON bc.group_id = cg.id
  WHERE cg.user_sub = accounts.user_sub
    AND cg.name = 'Credit Card Payments'
    AND bc.name = accounts.name
)
WHERE accounts.type = 'CREDIT_CARD';
