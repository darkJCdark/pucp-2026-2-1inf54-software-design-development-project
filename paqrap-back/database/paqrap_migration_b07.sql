-- B07: apply ONCE, after backup, before deploying the new backend.
-- Additive only: historical NULL means the original 120-minute MINOR policy.
ALTER TABLE breakdown_events ADD COLUMN minor_repair_minutes INT NULL;
CREATE TABLE breakdown_type_settings (
    breakdown_type VARCHAR(20) NOT NULL PRIMARY KEY,
    repair_minutes INT NOT NULL,
    CONSTRAINT chk_breakdown_repair_positive CHECK (repair_minutes > 0)
) ENGINE=InnoDB;
-- No UPDATE of existing events; no data deletion.
-- An absent MINOR settings row means 120 minutes.