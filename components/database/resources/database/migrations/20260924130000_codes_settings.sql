-- Every codes. setting is a row, so the code that reads them has no defaults
-- and an absent row means off. `or ignore` keeps whatever a garden has saved.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

INSERT OR IGNORE INTO settings (key, value) VALUES
  ('codes.accession_template', '{year}.{seq:0000}'),
  ('codes.accession_strict', '0'),
  ('codes.material_template', '{seq}'),
  ('codes.material_strict', '0'),
  ('codes.material_separator', '.');
