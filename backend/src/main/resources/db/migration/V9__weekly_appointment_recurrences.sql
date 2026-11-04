-- Recurring bookings are bounded materialized appointments, never an unbounded generator.
CREATE TABLE appointment_recurrences (
    id UUID PRIMARY KEY,
    barber_id UUID NOT NULL REFERENCES barbers(id),
    user_id UUID NOT NULL REFERENCES users(id),
    client_id UUID NOT NULL REFERENCES clients(id),
    service_id UUID NOT NULL REFERENCES services(id),
    starts_on DATE NOT NULL,
    local_time TIME NOT NULL,
    weekdays SMALLINT[] NOT NULL,
    weeks SMALLINT NOT NULL,
    price NUMERIC(12,2),
    notes TEXT,
    request_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT weekly_length_check CHECK (weeks IN (4,12,26)),
    CONSTRAINT weekly_days_check CHECK (
        cardinality(weekdays) BETWEEN 1 AND 7 AND weekdays <@ ARRAY[1,2,3,4,5,6,7]::smallint[]
    ),
    CONSTRAINT weekly_notes_check CHECK (notes IS NULL OR length(notes) <= 2000),
    CONSTRAINT weekly_price_check CHECK (price IS NULL OR price >= 0),
    CONSTRAINT weekly_idempotency UNIQUE (user_id,request_key)
);
CREATE INDEX appointment_recurrences_tenant ON appointment_recurrences(barber_id,created_at DESC);
ALTER TABLE appointments
    ADD COLUMN recurrence_id UUID REFERENCES appointment_recurrences(id);
CREATE INDEX appointments_recurrence_future
    ON appointments(barber_id,recurrence_id,starts_at) WHERE recurrence_id IS NOT NULL;
