-- Additive and safe to rerun if DEV DBA provisioned the schema ahead of
-- application rollout. Flyway still records the migration when executed.
-- Never embed users, passwords or password hashes in versioned migrations.
ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(40);

DO $migration$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
            WHERE conname = 'ck_users_username_format'
            AND conrelid = 'users'::regclass) THEN
        ALTER TABLE users ADD CONSTRAINT ck_users_username_format
            CHECK (username IS NULL OR username ~ '^[a-z][a-z0-9._-]{2,39}$');
    END IF;
END
$migration$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username_ci
    ON users (lower(username)) WHERE username IS NOT NULL;
