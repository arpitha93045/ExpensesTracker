ALTER TABLE users
    ADD COLUMN totp_secret       VARCHAR(64),
    ADD COLUMN totp_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN email_otp_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE two_factor_challenges (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64)  NOT NULL,
    method     VARCHAR(10)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_tfc_user_id ON two_factor_challenges (user_id);
