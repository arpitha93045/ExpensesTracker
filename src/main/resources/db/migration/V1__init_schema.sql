-- ============================================================
-- V1: Initial Schema - Smart Expense Analyzer
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Users ────────────────────────────────────────────────────
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL DEFAULT 'ROLE_USER',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- ── Refresh Tokens (revocable, one per session/device) ───────
CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    device_info VARCHAR(255),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rt_user_id    ON refresh_tokens(user_id);
CREATE INDEX idx_rt_token_hash ON refresh_tokens(token_hash);

-- ── Categories (system global + per-user custom) ─────────────
CREATE TABLE categories (
    id         SERIAL       PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    icon       VARCHAR(50),
    color      VARCHAR(7),
    user_id    UUID         REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (name, user_id)
);

-- ── Upload Jobs ───────────────────────────────────────────────
CREATE TABLE upload_jobs (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name      VARCHAR(255) NOT NULL,
    file_path      VARCHAR(500) NOT NULL,
    file_type      VARCHAR(10)  NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    total_rows     INT,
    processed_rows INT          NOT NULL DEFAULT 0,
    error_message  TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at   TIMESTAMPTZ
);

CREATE INDEX idx_upload_jobs_user_id ON upload_jobs(user_id);
CREATE INDEX idx_upload_jobs_status  ON upload_jobs(status);

-- ── Transactions ─────────────────────────────────────────────
CREATE TABLE transactions (
    id                   UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    upload_job_id        UUID           REFERENCES upload_jobs(id) ON DELETE SET NULL,
    category_id          INT            REFERENCES categories(id) ON DELETE SET NULL,
    description          TEXT           NOT NULL,
    amount               NUMERIC(15, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL DEFAULT 'USD',
    transaction_date     DATE           NOT NULL,
    transaction_type     VARCHAR(10)    NOT NULL,
    merchant             VARCHAR(255),
    raw_text             TEXT,
    ai_categorized       BOOLEAN        NOT NULL DEFAULT FALSE,
    ai_confidence        NUMERIC(4, 3),
    categorization_note  TEXT,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_txn_user_id    ON transactions(user_id);
CREATE INDEX idx_txn_user_date  ON transactions(user_id, transaction_date DESC);
CREATE INDEX idx_txn_category   ON transactions(category_id);
CREATE INDEX idx_txn_upload_job ON transactions(upload_job_id);
CREATE INDEX idx_txn_type       ON transactions(transaction_type);
CREATE INDEX idx_txn_debits     ON transactions(user_id, transaction_date)
    WHERE transaction_type = 'DEBIT';

-- ── Auto-update updated_at trigger ───────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
