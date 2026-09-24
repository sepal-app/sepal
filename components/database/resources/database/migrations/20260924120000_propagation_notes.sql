-- One free-text field on a propagation, for what the structured columns do not
-- hold: a different medium tried, a treatment, why a batch failed.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

ALTER TABLE propagation ADD COLUMN notes text;
