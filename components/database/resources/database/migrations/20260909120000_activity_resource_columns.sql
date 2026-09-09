-- Which record an activity event concerns, as two indexed columns instead of a
-- JSON path.
--
-- Until now the subject lived inside `data` under a per-type key -- an
-- accession event carried `accession-id`, a taxon event `taxon-id` -- and every
-- reader dug it back out with `data ->> 'accession-id'` and a cast. No index can
-- serve that, so a record's history page scanned the whole table, and the
-- changelog did it four times over in LEFT JOINs.
--
-- Both columns are nullable because two event types have no subject:
-- `settings/updated` and `setup/completed`. They stay null and that is correct,
-- not missing data.
--
-- The index is (resource_type, resource_id), which is the opposite order from
-- media_link, note and tag_link. Those are looked up by id; this one is also
-- filtered by type alone -- "every accession event" -- and a leading
-- resource_type serves both that and the pair. A leading resource_id would
-- serve only the pair.
--
-- The backfill below populates the columns and deliberately does NOT rewrite
-- `data`. Rows written before this migration keep their now-redundant subject
-- key. Two reasons. It leaves the original readable if a branch below turns out
-- to be wrong, which an UPDATE over `data` would have destroyed. And a build
-- rolled back to before this migration still finds its own key, so per-record
-- history does not go blank across a whole garden on the way back.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

ALTER TABLE activity ADD COLUMN resource_type text;
ALTER TABLE activity ADD COLUMN resource_id integer;

CREATE INDEX activity_resource_type_resource_id_idx
  on activity (resource_type, resource_id);

-- The subject is the record the event is about. For a child resource -- a note,
-- a tag link, a synonym -- that is the record it hangs on, not the child. The
-- child's own id stays in `data`, which is the only place it is recorded.

UPDATE activity SET resource_type = 'accession',
                    resource_id = CAST(data ->> 'accession-id' AS integer)
 WHERE type LIKE 'accession/%';

UPDATE activity SET resource_type = 'material',
                    resource_id = CAST(data ->> 'material-id' AS integer)
 WHERE type LIKE 'material/%';

UPDATE activity SET resource_type = 'location',
                    resource_id = CAST(data ->> 'location-id' AS integer)
 WHERE type LIKE 'location/%';

UPDATE activity SET resource_type = 'taxon',
                    resource_id = CAST(data ->> 'taxon-id' AS integer)
 WHERE type LIKE 'taxon/%';

UPDATE activity SET resource_type = 'contact',
                    resource_id = CAST(data ->> 'contact-id' AS integer)
 WHERE type LIKE 'contact/%';

UPDATE activity SET resource_type = 'media',
                    resource_id = CAST(data ->> 'media-id' AS integer)
 WHERE type LIKE 'media/%';

UPDATE activity SET resource_type = 'user',
                    resource_id = CAST(data ->> 'user-id' AS integer)
 WHERE type LIKE 'user/%';

-- A synonym hangs on a taxon, so a synonym event is a taxon event.
UPDATE activity SET resource_type = 'taxon',
                    resource_id = CAST(data ->> 'taxon-id' AS integer)
 WHERE type LIKE 'synonym/%';

-- tag/created, /updated and /deleted are about the tag itself. tag/linked and
-- /unlinked are about the record that was tagged, so they fall to the clause
-- below with the notes. The two groups are listed explicitly rather than matched
-- with LIKE 'tag/%', which would swallow both.
UPDATE activity SET resource_type = 'tag',
                    resource_id = CAST(data ->> 'tag-id' AS integer)
 WHERE type IN ('tag/created', 'tag/updated', 'tag/deleted');

-- These two already carry the subject generically, which is why they are the
-- only payloads that need no per-type branch.
UPDATE activity SET resource_type = data ->> 'resource-type',
                    resource_id = CAST(data ->> 'resource-id' AS integer)
 WHERE type LIKE 'note/%'
    OR type IN ('tag/linked', 'tag/unlinked');
