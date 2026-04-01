-- Seed default category groups and their categories
DO $$
DECLARE
  g_housing   UUID;
  g_food      UUID;
  g_transport UUID;
  g_savings   UUID;
BEGIN
  INSERT INTO category_groups (name, sort_order) VALUES ('Housing',   1) RETURNING id INTO g_housing;
  INSERT INTO category_groups (name, sort_order) VALUES ('Food',      2) RETURNING id INTO g_food;
  INSERT INTO category_groups (name, sort_order) VALUES ('Transport', 3) RETURNING id INTO g_transport;
  INSERT INTO category_groups (name, sort_order) VALUES ('Savings',   4) RETURNING id INTO g_savings;

  INSERT INTO budget_categories (group_id, name, sort_order) VALUES
    (g_housing,   'Rent / Mortgage', 1),
    (g_housing,   'Utilities',       2),
    (g_housing,   'Internet',        3),
    (g_housing,   'Home Insurance',  4);

  INSERT INTO budget_categories (group_id, name, sort_order) VALUES
    (g_food, 'Groceries',  1),
    (g_food, 'Dining Out', 2);

  INSERT INTO budget_categories (group_id, name, sort_order) VALUES
    (g_transport, 'Gas',            1),
    (g_transport, 'Car Insurance',  2),
    (g_transport, 'Public Transit', 3);

  INSERT INTO budget_categories (group_id, name, sort_order) VALUES
    (g_savings, 'Emergency Fund', 1),
    (g_savings, 'Retirement',     2),
    (g_savings, 'Goals',          3);
END $$;
