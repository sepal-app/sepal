-- Probe: the taxon rebuild that replaces the rank CHECK with a lookup table.
-- Run against a VACUUM INTO copy of a real database. See PROBE.md.
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- adds those, and this must be provable in the same shape it will run.

CREATE TABLE taxon_rank (name text primary key) strict;

-- Every value is a single word, so it round-trips unchanged through the
-- csk/->kebab-case-{keyword,string} transforms on taxon.spec. `aggregate`
-- rather than `species_aggregate` for exactly that reason -- see the plan.
INSERT INTO taxon_rank (name) VALUES
  ('aggregate'), ('class'), ('convariety'), ('cultivar'), ('family'), ('form'),
  ('genus'), ('grex'), ('group'), ('kingdom'), ('lusus'), ('order'),
  ('phylum'), ('prole'), ('section'), ('series'), ('species'), ('subclass'),
  ('subfamily'), ('subform'), ('subgenus'), ('subkingdom'), ('suborder'),
  ('subphylum'), ('subsection'), ('subseries'), ('subspecies'), ('subtribe'),
  ('subvariety'), ('superclass'), ('superfamily'), ('superorder'),
  ('supertribe'), ('tribe'), ('unranked'), ('variety');

CREATE TABLE taxon_new (
  id integer primary key autoincrement,
  name text not null,
  author text,
  parent_id integer references taxon(id),
  rank text not null references taxon_rank(name),
  wfo_taxon_id text,
  vernacular_names text not null default '[]' check(json_valid(vernacular_names)),
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;

INSERT INTO taxon_new (id, name, author, parent_id, rank, wfo_taxon_id,
                       vernacular_names, created_at, updated_at)
SELECT id, name, author, parent_id, rank, wfo_taxon_id,
       vernacular_names, created_at, updated_at
FROM taxon;

DROP TABLE taxon;

ALTER TABLE taxon_new RENAME TO taxon;

CREATE INDEX taxon_id_idx on taxon (id);
CREATE INDEX taxon_name_idx on taxon (name);
CREATE INDEX taxon_parent_id_idx on taxon (parent_id);
CREATE INDEX taxon_wfo_taxon_id_idx on taxon (wfo_taxon_id);

CREATE TRIGGER trigger_taxon_updated_at after update on taxon
begin
  update taxon set updated_at = datetime('now') where id = NEW.id;
end;
CREATE TRIGGER trigger_taxon_after_insert after insert on taxon begin
  insert into taxon_fts(rowid, name) values (new.id, new.name);
end;
CREATE TRIGGER trigger_taxon_after_delete after delete on taxon begin
  insert into taxon_fts(taxon_fts, rowid, name) values('delete', old.id, old.name);
end;
CREATE TRIGGER trigger_taxon_after_update after update on taxon begin
  insert into taxon_fts(taxon_fts, rowid, name) values('delete', old.id, old.name);
  insert into taxon_fts(rowid, name) values (new.id, new.name);
end;

INSERT INTO taxon_fts(taxon_fts) VALUES('rebuild');

-- The guard. A bare `pragma foreign_key_check` prints violations and exits 0,
-- so it fails nothing. Feeding its row count through a CHECK does: on a
-- violation this raises, and -bail rolls the wrapping transaction back.
CREATE TEMP TABLE fk_guard (n integer check (n = 0));
INSERT INTO fk_guard SELECT count(*) FROM pragma_foreign_key_check;
DROP TABLE fk_guard;
