-- V10: separate, auditable customer debt ledger, isolated by barber.
CREATE TABLE client_debt_entries (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 barber_id UUID NOT NULL,
 client_id UUID NOT NULL,
 amount NUMERIC(12,2) NOT NULL,
 note VARCHAR(255),
 request_key UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT fk_client_debts_same_barber FOREIGN KEY (barber_id,client_id)
  REFERENCES clients(barber_id,id) ON DELETE RESTRICT,
 CONSTRAINT uq_client_debts_request UNIQUE (barber_id,request_key),
 CONSTRAINT ck_client_debts_amount CHECK (amount <> 0)
);
CREATE INDEX idx_client_debts_client ON client_debt_entries(barber_id,client_id,created_at DESC);
