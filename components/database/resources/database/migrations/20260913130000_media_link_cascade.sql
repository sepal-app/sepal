-- media_link.media_id had no foreign key, so deleting a media row left its
-- link row behind pointing at nothing. Every media deletion since the feature
-- shipped has done this. SQLite cannot add a foreign key to an existing table,
-- so media_link is rebuilt -- the same 12-step rebuild taxon and material went
-- through in the taxon_rank_lookup and material_history migrations.
--
-- Chosen over calling media.i/unlink! from the delete handler: that fixes the
-- one caller that exists and leaves the next one to remember.
--
-- Nothing references media_link, so the rebuild needs no fixups beyond its own
-- index and trigger.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

CREATE TABLE media_link_new (
  id integer primary key autoincrement,
  media_id integer not null unique references media(id) on delete cascade,
  resource_id integer not null,
  resource_type text not null,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;

-- The already-orphaned rows do not come across. A link to a media row that no
-- longer exists cannot be repaired, only dropped.
INSERT INTO media_link_new (id, media_id, resource_id, resource_type, created_at, updated_at)
SELECT ml.id, ml.media_id, ml.resource_id, ml.resource_type, ml.created_at, ml.updated_at
FROM media_link ml
WHERE ml.media_id IN (SELECT id FROM media);

DROP TABLE media_link;

ALTER TABLE media_link_new RENAME TO media_link;

CREATE INDEX media_link_media_id_idx on media_link (media_id);
CREATE INDEX media_link_resource_id_resource_type_idx on media_link (resource_id, resource_type);

CREATE TRIGGER trigger_media_link_updated_at after update on media_link
begin
  update media_link set updated_at = datetime('now') where id = NEW.id;
end;

-- The guard. A bare `pragma foreign_key_check` prints violations and exits 0,
-- so it fails nothing. Feeding its row count through a CHECK does.
CREATE TEMP TABLE fk_guard (n integer check (n = 0));
INSERT INTO fk_guard SELECT count(*) FROM pragma_foreign_key_check;
DROP TABLE fk_guard;
