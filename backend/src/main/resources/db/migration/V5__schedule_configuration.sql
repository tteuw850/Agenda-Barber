-- F02-03: additive operational schedule configuration. Keep V1-V4 unchanged.
ALTER TABLE blocked_periods ADD COLUMN notes VARCHAR(2000);

CREATE TABLE recurring_blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    barber_id UUID NOT NULL REFERENCES barbers(id) ON DELETE RESTRICT,
    weekday SMALLINT NOT NULL,
    starts_at TIME NOT NULL,
    ends_at TIME NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    reason VARCHAR(500),
    notes VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_recurring_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT ck_recurring_span CHECK (starts_at < ends_at),
    CONSTRAINT ck_recurring_range CHECK (valid_until >= valid_from AND valid_until <= valid_from + 180)
);
CREATE INDEX idx_recurring_blocks_barber_weekday
    ON recurring_blocks(barber_id, weekday, valid_from, valid_until);
CREATE TRIGGER trg_recurring_blocks_updated BEFORE UPDATE ON recurring_blocks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
