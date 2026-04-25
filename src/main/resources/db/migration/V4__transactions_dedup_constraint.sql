-- ============================================================
-- V4: Deduplication — remove existing duplicates, then add
-- a unique constraint to prevent new ones.
-- For each group of (user_id, transaction_date, amount,
-- transaction_type, description), keep the newest row and
-- delete the rest.
-- ============================================================

DELETE FROM transactions
WHERE id IN (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY user_id, transaction_date, amount, transaction_type, description
                   ORDER BY created_at DESC
               ) AS rn
        FROM transactions
    ) ranked
    WHERE rn > 1
);

ALTER TABLE transactions
    ADD CONSTRAINT uq_txn_user_date_amount_type_desc
        UNIQUE (user_id, transaction_date, amount, transaction_type, description);
