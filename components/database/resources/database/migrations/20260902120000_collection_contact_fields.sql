-- Collection and contact fields: three leftovers from 028's survey that were
-- too small to carry a plan alone -- a collector's own number for a gathering,
-- uncertainty on the elevation figure, and what kind of party a contact is.
--
-- All three are plain ADD COLUMN. None carries a CHECK: SQLite cannot add one
-- by ALTER TABLE, and none of these columns justifies a table rebuild.
-- Enforcement is spec-only in the collection and contact interface specs.

ALTER TABLE collection ADD COLUMN collectors_code text;
ALTER TABLE collection ADD COLUMN elevation_accuracy integer;
ALTER TABLE contact ADD COLUMN type text;
