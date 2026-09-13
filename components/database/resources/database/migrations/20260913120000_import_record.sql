-- Which row in a source system a record was imported from.
--
-- `load-import` reads a directory of records, each carrying the id it had in
-- whatever system produced it, and writes them through the component
-- interfaces. That mapping -- source id to the Sepal row it became -- was only
-- ever written to `loaded.json` beside the input. A file next to the input is
-- the wrong place for it: it is how a later import resolves the record an
-- event or a history row concerns, and if the file is lost the link is gone.
--
-- Deliberately a table of its own rather than a column on each of the eight
-- importable tables. Nothing joins to it and no existing spec learns a new
-- field, so a record that was imported is indistinguishable from one that was
-- typed in -- which is right. Sepal never reads this during normal operation.
--
-- `source_table` is the file the record came from, not a Sepal table name:
-- three of the source files feed one Sepal table, and two Sepal tables are fed
-- by the same file. `source_id` is opaque text, never parsed -- `accession_note:1`
-- and `species_synonym-3` are both real.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

CREATE TABLE import_record (
  id integer primary key autoincrement,
  source_table text not null,
  source_id text not null,
  resource_type text not null,
  resource_id integer not null,
  created_at text not null default (datetime('now'))
);

-- One Sepal row per source row. A second load of the same input is a mistake,
-- and this is what makes it a loud one rather than a silent duplicate.
CREATE UNIQUE INDEX import_record_source_table_source_id_idx
  on import_record (source_table, source_id);

-- The other direction: what did this record come from? Ordered
-- (resource_type, resource_id) to match the convention 025 set on `activity`,
-- so a type alone -- "every imported accession" -- is served by the same index.
CREATE INDEX import_record_resource_type_resource_id_idx
  on import_record (resource_type, resource_id);
