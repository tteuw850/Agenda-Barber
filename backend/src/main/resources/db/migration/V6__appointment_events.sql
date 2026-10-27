-- F02-04: audit trail. Preserve V1-V5 and never rewrite price snapshots.
ALTER TABLE appointments ADD CONSTRAINT uq_appointments_barber_id UNIQUE (barber_id,id);

CREATE TABLE appointment_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    barber_id UUID NOT NULL REFERENCES barbers(id) ON DELETE RESTRICT,
    appointment_id UUID NOT NULL REFERENCES appointments(id) ON DELETE RESTRICT,
    event_type VARCHAR(16) NOT NULL,
    previous_status appointment_status,
    new_status appointment_status NOT NULL,
    happened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_appointment_events_type CHECK
        (event_type IN ('CREATED','EDITED','STARTED','COMPLETED','CANCELLED')),
    CONSTRAINT fk_appointment_events_barber_appt FOREIGN KEY (barber_id, appointment_id)
        REFERENCES appointments(barber_id,id) ON DELETE RESTRICT
);
CREATE INDEX idx_appointment_events_history
    ON appointment_events(barber_id, appointment_id, happened_at, id);
