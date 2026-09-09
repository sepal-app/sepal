-- Accession receipt: what form material arrived in, and how much of it.
--
-- received_type carries a 28-value propagule vocabulary, stored snake_case and
-- enforced by a foreign key into a lookup table rather than by a CHECK. Both
-- put the invariant in the database; only the table lets a 29th value arrive
-- as an INSERT instead of a full accession rebuild with its FTS triggers. The
-- Malli enum in the accession spec is kept for coercion and for the form's
-- labels, and a test asserts the two agree in both directions.
--
-- quantity_received is not material.quantity: one is how many propagules
-- arrived, the other how many plants exist now. 0 is a legitimate value --
-- an accession recorded with nothing received -- so there is no default.
--
-- ADD COLUMN takes both a REFERENCES and a CHECK clause when the new column's
-- default is NULL, so neither constraint costs a table rebuild and
-- accession_fts and its three triggers stay untouched.

CREATE TABLE accession_received_type (
  name text primary key
) strict;

INSERT INTO accession_received_type (name) VALUES
  ('air_layer'), ('balled_and_burlapped'), ('bare_root_plant'),
  ('bud_cutting'), ('budded'), ('bulb'), ('bulbil'), ('clump'), ('corm'),
  ('division'), ('graft'), ('layer'), ('plant'), ('pseudobulb'), ('rhizome'),
  ('root'), ('root_cutting'), ('root_sucker'), ('rooted_cutting'), ('scion'),
  ('seed'), ('seedling'), ('spore'), ('sporeling'), ('tuber'), ('unknown'),
  ('unrooted_cutting'), ('vegetative_spreading');

ALTER TABLE accession ADD COLUMN received_type text
  REFERENCES accession_received_type(name);
ALTER TABLE accession ADD COLUMN quantity_received integer
  CHECK(quantity_received >= 0);
