-- F02-01: additive client metadata; V1-V3 checksums remain unchanged.
-- Before production migration, inspect duplicates of (barber_id, phone).
ALTER TABLE clients
    ADD COLUMN photo_url VARCHAR(512),
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE clients ADD CONSTRAINT ck_clients_photo_url
    CHECK (photo_url IS NULL OR photo_url ~ '^https://[^[:space:]]+$');

CREATE UNIQUE INDEX uq_clients_barber_phone
    ON clients (barber_id, phone) WHERE phone IS NOT NULL;

CREATE INDEX idx_clients_barber_favorite
    ON clients (barber_id, favorite) WHERE active = TRUE;

CREATE INDEX idx_appointments_barber_client_status
    ON appointments (barber_id, client_id, status, starts_at DESC);
