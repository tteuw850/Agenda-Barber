-- F03-01: capture the amount actually received, independently from booking price.
-- Existing COMPLETED rows remain NULL: historical receipts must not be fabricated.
ALTER TABLE appointments
    ADD COLUMN received_amount NUMERIC(12,2);
ALTER TABLE appointments
    ADD CONSTRAINT ck_appointments_received_amount
    CHECK (received_amount IS NULL OR
           (status = 'COMPLETED' AND received_amount >= 0));

-- Deleting an expense hides it from the ledger without discarding its audit trail.
ALTER TABLE expenses ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE INDEX idx_expenses_active_by_barber_date
    ON expenses(barber_id, occurred_on DESC, id)
    WHERE deleted_at IS NULL;

COMMENT ON COLUMN appointments.received_amount IS
    'Actual receipt confirmed at completion; NULL for legacy rows with unknown receipts.';
