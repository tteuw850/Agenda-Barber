-- F02-02: visual semantics for UX-07; additive migration, no change to V1 or V2.
ALTER TABLE services
    ADD COLUMN icon_key VARCHAR(32) NOT NULL DEFAULT 'SCISSORS';

ALTER TABLE services
    ADD CONSTRAINT ck_services_icon_key
        CHECK (icon_key IN ('SCISSORS', 'BEARD', 'COMBO', 'EYEBROW', 'CHILD', 'OTHER'));
