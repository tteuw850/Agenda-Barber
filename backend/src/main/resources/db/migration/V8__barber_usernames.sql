-- Optional usernames keep existing email logins compatible with older APKs.
-- Provision usernames and BCrypt password hashes only in the target environment,
-- never in a migration or the Git repository.
ALTER TABLE users ADD COLUMN username VARCHAR(40);

ALTER TABLE users ADD CONSTRAINT ck_users_username_format
    CHECK (username IS NULL OR username ~ '^[a-z][a-z0-9._-]{2,39}$');

CREATE UNIQUE INDEX uq_users_username_ci
    ON users (lower(username)) WHERE username IS NOT NULL;
