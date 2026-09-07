-- accession.intended_location_id: where accessioned material is meant to go
-- before it is planted. Nullable, because nearly every accession has none.
--
-- No `on delete` clause, so a location an accession intends cannot be deleted
-- while it does. That is the same rule material.location_id already imposes,
-- and it keeps the record of what was planned.
ALTER TABLE accession ADD COLUMN intended_location_id integer references location(id);

CREATE INDEX accession_intended_location_id_idx on accession (intended_location_id);
