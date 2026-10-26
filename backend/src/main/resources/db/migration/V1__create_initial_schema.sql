-- Agenda Barber F01-03 / PostgreSQL 17. Timestamps are UTC instants.
-- The application role is read/write only; migrations require a separate owner.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TYPE user_role AS ENUM ('ADMIN', 'BARBER');
CREATE TYPE appointment_status AS ENUM
    ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW');

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role user_role NOT NULL DEFAULT 'BARBER',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_email CHECK (position('@' IN email) > 1 AND btrim(email) = email),
    CONSTRAINT ck_users_hash CHECK (length(btrim(password_hash)) >= 20)
);
CREATE UNIQUE INDEX uq_users_email_ci ON users (lower(email));

CREATE TABLE barbers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    timezone VARCHAR(64) NOT NULL DEFAULT 'America/Sao_Paulo',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_barbers_timezone CHECK (btrim(timezone) <> '')
);

CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    barber_id UUID NOT NULL REFERENCES barbers(id) ON DELETE RESTRICT,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(32),
    email VARCHAR(254),
    birth_date DATE,
    notes TEXT,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_clients_barber_id UNIQUE (barber_id, id),
    CONSTRAINT ck_clients_name CHECK (btrim(name) <> '')
);
CREATE INDEX idx_clients_barber_name ON clients (barber_id, lower(name));

CREATE TABLE services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    barber_id UUID NOT NULL REFERENCES barbers(id) ON DELETE RESTRICT,
    name VARCHAR(150) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    default_price NUMERIC(12,2) NOT NULL,
    is_haircut BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_services_barber_id UNIQUE (barber_id, id),
    CONSTRAINT ck_services_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_services_duration CHECK (duration_minutes BETWEEN 1 AND 480),
    CONSTRAINT ck_services_price CHECK (default_price >= 0)
);
CREATE INDEX idx_services_barber_active ON services (barber_id, active);

CREATE TABLE appointments (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 barber_id UUID NOT NULL REFERENCES barbers(id) ON DELETE RESTRICT,
 client_id UUID NOT NULL, service_id UUID NOT NULL,
 starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL,
 service_name_snapshot VARCHAR(150) NOT NULL,
 price_snapshot NUMERIC(12,2) NOT NULL,
 status appointment_status NOT NULL DEFAULT 'SCHEDULED',
 notes TEXT, completed_at TIMESTAMPTZ,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT fk_appointments_client_same_barber
  FOREIGN KEY (barber_id, client_id) REFERENCES clients(barber_id,id) ON DELETE RESTRICT,
 CONSTRAINT fk_appointments_service_same_barber
  FOREIGN KEY (barber_id, service_id) REFERENCES services(barber_id,id) ON DELETE RESTRICT,
 CONSTRAINT ck_appointments_time CHECK (ends_at > starts_at),
 CONSTRAINT ck_appointments_snapshot CHECK (btrim(service_name_snapshot) <> '' AND price_snapshot >= 0),
 CONSTRAINT ck_appointments_version CHECK (version >= 0),
 CONSTRAINT ck_appointments_completed CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
 CONSTRAINT ex_appointments_barber_active_overlap EXCLUDE USING gist
 (barber_id WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&)
 WHERE (status IN ('SCHEDULED','IN_PROGRESS'))
);
CREATE INDEX idx_appointments_barber_start ON appointments(barber_id,starts_at);
CREATE INDEX idx_appointments_barber_status_start ON appointments(barber_id,status,starts_at);
CREATE TABLE blocked_periods (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 barber_id UUID NOT NULL REFERENCES barbers(id) ON DELETE RESTRICT,
 starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL,
 reason VARCHAR(500), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT ck_blocked_periods_time CHECK (ends_at > starts_at)
);
CREATE INDEX idx_blocked_periods_barber_start ON blocked_periods(barber_id,starts_at);
CREATE TABLE working_hours (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 barber_id UUID NOT NULL REFERENCES barbers(id) ON DELETE RESTRICT,
 weekday SMALLINT NOT NULL,
 opens_at TIME NOT NULL, closes_at TIME NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT uq_working_hours_slot UNIQUE (barber_id,weekday,opens_at,closes_at),
 CONSTRAINT ck_working_hours_weekday CHECK (weekday BETWEEN 1 AND 7),
 CONSTRAINT ck_working_hours_span CHECK (opens_at < closes_at)
);
CREATE INDEX idx_working_hours_barber_weekday ON working_hours(barber_id,weekday);
CREATE TABLE expenses (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 barber_id UUID NOT NULL REFERENCES barbers(id) ON DELETE RESTRICT,
 occurred_on DATE NOT NULL, description VARCHAR(255) NOT NULL,
 amount NUMERIC(12,2) NOT NULL, category VARCHAR(80) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT ck_expenses_amount CHECK (amount > 0),
 CONSTRAINT ck_expenses_description CHECK (btrim(description) <> ''),
 CONSTRAINT ck_expenses_category CHECK (btrim(category) <> '')
);
CREATE INDEX idx_expenses_barber_date ON expenses(barber_id,occurred_on DESC);
CREATE TABLE idempotency_records (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 idempotency_key VARCHAR(128) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 operation VARCHAR(100) NOT NULL,
 resource_id UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT uq_idempotency_user_key UNIQUE (user_id,idempotency_key),
 CONSTRAINT ck_idempotency_key CHECK (btrim(idempotency_key) <> ''),
 CONSTRAINT ck_idempotency_hash CHECK (request_hash ~ '^[0-9a-fA-F]{64}$'),
 CONSTRAINT ck_idempotency_operation CHECK (btrim(operation) <> '')
);
CREATE INDEX idx_idempotency_user_created ON idempotency_records(user_id,created_at DESC);
CREATE FUNCTION set_updated_at() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END; $$;
CREATE TRIGGER trg_users_updated BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_barbers_updated BEFORE UPDATE ON barbers FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_clients_updated BEFORE UPDATE ON clients FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_services_updated BEFORE UPDATE ON services FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_appointments_updated BEFORE UPDATE ON appointments FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_blocked_periods_updated BEFORE UPDATE ON blocked_periods FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_working_hours_updated BEFORE UPDATE ON working_hours FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_expenses_updated BEFORE UPDATE ON expenses FOR EACH ROW EXECUTE FUNCTION set_updated_at();
