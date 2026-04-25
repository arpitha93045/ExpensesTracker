-- ============================================================
-- V3: Budgets table
-- ============================================================

CREATE TABLE budgets (
    id          SERIAL       PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id INT          NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    amount      NUMERIC(15,2) NOT NULL,
    year_month  VARCHAR(7)   NOT NULL,   -- e.g. '2025-04'
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, category_id, year_month)
);

CREATE INDEX idx_budgets_user_month ON budgets(user_id, year_month);

CREATE TRIGGER trg_budgets_updated_at
    BEFORE UPDATE ON budgets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
