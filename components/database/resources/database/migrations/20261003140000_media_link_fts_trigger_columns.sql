-- media_link's media_fts trigger fires only when a link's target changes. An
-- unrestricted AFTER UPDATE also fires on trigger_media_link_updated_at's own
-- update of updated_at, which refreshes every relinked row twice.
--
-- media_fts_source reads media, media_link, accession, material, taxon and
-- location. A migration that rebuilds one of them by copy, drop and rename
-- has to drop the view and the triggers that read it first, and recreate
-- them after: SQLite checks the view when the table is renamed.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

DROP TRIGGER IF EXISTS trigger_media_link_media_fts_update;

CREATE TRIGGER trigger_media_link_media_fts_update
  AFTER UPDATE OF media_id, resource_type, resource_id ON media_link BEGIN
  DELETE FROM media_fts WHERE rowid IN (old.media_id, new.media_id);
  INSERT INTO media_fts(rowid, title, description, linked)
    SELECT id, title, description, linked FROM media_fts_source
    WHERE id IN (old.media_id, new.media_id);
END;
