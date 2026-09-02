-- 032 Taxon Synonymy: a garden's own record that a name is a synonym of one of
-- its taxa.
--
-- `synonym_name` is text, not a foreign key. A garden may assert a synonym for
-- a name that is not, and should not become, a taxon row. WFO's own synonyms
-- are never in this table; they live in a read-only reference file.
--
-- No unique constraint on (taxon_id, synonym_name): WFO itself has one name
-- string that is a synonym of two accepted taxa, and a garden disagreeing with
-- itself is legitimate data, not corruption.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

CREATE TABLE taxon_synonym (
  id integer primary key autoincrement,
  taxon_id integer not null references taxon(id),
  synonym_name text not null,
  source text not null default 'local' check(source in ('local', 'imported')),
  created_by integer references "user"(id),
  created_at text not null default (datetime('now'))
) strict;

CREATE INDEX taxon_synonym_taxon_id_idx on taxon_synonym (taxon_id);
CREATE INDEX taxon_synonym_name_idx on taxon_synonym (synonym_name collate nocase);
