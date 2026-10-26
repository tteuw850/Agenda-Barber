-- F01-05B: retain Flyway V1 unchanged. Refresh token values are never persisted.
ALTER TABLE users ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD CONSTRAINT ck_users_credential_version CHECK (credential_version >= 0);

CREATE TABLE auth_refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    session_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    credential_version BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_refresh_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_refresh_version CHECK (credential_version >= 0),
    CONSTRAINT ck_refresh_expiry CHECK (expires_at > created_at)
);
CREATE INDEX idx_refresh_user_session ON auth_refresh_tokens(user_id, session_id);
CREATE INDEX idx_refresh_expiry ON auth_refresh_tokens(expires_at);
