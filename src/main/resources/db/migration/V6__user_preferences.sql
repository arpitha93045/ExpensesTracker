ALTER TABLE users
    ADD COLUMN default_currency      VARCHAR(3) NOT NULL DEFAULT 'INR',
    ADD COLUMN notifications_enabled BOOLEAN    NOT NULL DEFAULT TRUE;
