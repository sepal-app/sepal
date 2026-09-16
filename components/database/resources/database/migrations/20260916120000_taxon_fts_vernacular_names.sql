-- Search a plant by what people call it, not only by its scientific name.
--
-- taxon_fts indexed `name` alone, so "Japanese maple" found nothing. Every
-- search that matches a taxon name goes through this one table -- taxa,
-- accessions, material and locations all declare :fts-table :taxon_fts -- and
-- FTS5 MATCH searches every column of a table, so a second column reaches all
-- four with no change to the query compiler.
--
-- Only the names are indexed, not the languages beside them and not the JSON
-- keys: `[{"name":"momiji","language":"Nihongo"}]` indexes `momiji` alone.
-- Indexing the column as stored would have been one line, but the tokenizer
-- splits on punctuation, so `name`, `language` and every language value would
-- have become search terms too.
--
-- That means the text has to be computed, and the table can no longer take its
-- content from `taxon`: FTS5 prepares its content queries in a context where
-- json_each does not resolve, so neither content='taxon' with a generated
-- column nor content=<a view> works -- both fail the rebuild with "no such
-- table: main.json_each". The table therefore keeps its own copy of the text
-- and the triggers compute it. Measured 2026-09-16 on 453,167 taxa: the
-- backfill is 0.9s, which a garden's first request pays once inside the
-- dispatcher's start lock.

DROP TRIGGER trigger_taxon_after_insert;
DROP TRIGGER trigger_taxon_after_delete;
DROP TRIGGER trigger_taxon_after_update;
DROP TABLE taxon_fts;

CREATE VIRTUAL TABLE taxon_fts USING fts5(name, vernacular_names);

INSERT INTO taxon_fts(rowid, name, vernacular_names)
SELECT id,
       name,
       (SELECT group_concat(json_extract(value, '$.name'), ' ')
          FROM json_each(taxon.vernacular_names))
  FROM taxon;

-- The table holds its own text now, so a delete is an ordinary DELETE rather
-- than the 'delete' command an external-content table needs, and it does not
-- have to be handed back the values that were indexed.
CREATE TRIGGER trigger_taxon_after_insert after insert on taxon begin
  insert into taxon_fts(rowid, name, vernacular_names)
  values (new.id,
          new.name,
          (select group_concat(json_extract(value, '$.name'), ' ')
             from json_each(new.vernacular_names)));
end;

CREATE TRIGGER trigger_taxon_after_delete after delete on taxon begin
  delete from taxon_fts where rowid = old.id;
end;

CREATE TRIGGER trigger_taxon_after_update after update on taxon begin
  delete from taxon_fts where rowid = old.id;
  insert into taxon_fts(rowid, name, vernacular_names)
  values (new.id,
          new.name,
          (select group_concat(json_extract(value, '$.name'), ' ')
             from json_each(new.vernacular_names)));
end;
