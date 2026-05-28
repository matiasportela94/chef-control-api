-- ============================================================
-- Chef Control — V2: Password Reset Tokens
-- ============================================================

CREATE TABLE password_reset_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id),
    token       UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    type        VARCHAR(20) NOT NULL CHECK (type IN ('PASSWORD_RESET', 'SET_PASSWORD')),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ON password_reset_tokens (token);
CREATE INDEX ON password_reset_tokens (user_id) WHERE used_at IS NULL;
