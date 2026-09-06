-- 031 Tags: a curator's ad-hoc groupings, closing 028's tag gap -- 23 tags and
-- 447 links in the Belize Botanic Gardens backup.
--
-- Bauble's own data reads as ephemeral label-printing batches, not a
-- taxonomy -- most of the 23 names are print-run labels ("TO PRINT BLOCK 1",
-- "ZA labels"), thrown away once printed (plans/031-tags.md). That argues
-- against hierarchy, colour or validation here: a tag is cheap to make and
-- cheap to delete.
--
-- `tag_link` follows `media_link` (schema.sql:145-152): one polymorphic link
-- table per cross-cutting resource, `resource_id` + `resource_type` rather
-- than three separate FK columns. Unlike `media_link`, `tag_link` carries a
-- unique index across all three columns -- tagging the same resource twice
-- with the same tag is meaningless, and the UI would otherwise have to
-- prevent it itself.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

CREATE TABLE tag (
  id integer primary key autoincrement,
  name text not null unique collate nocase,
  description text,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;

CREATE TRIGGER trigger_tag_updated_at after update on tag
begin
  update tag set updated_at = datetime('now') where id = NEW.id;
end;

CREATE TABLE tag_link (
  id integer primary key autoincrement,
  tag_id integer not null references tag(id),
  resource_id integer not null,
  resource_type text not null,
  created_at text not null default (datetime('now'))
) strict;

CREATE UNIQUE INDEX tag_link_unique_idx on tag_link (tag_id, resource_id, resource_type);
CREATE INDEX tag_link_resource_id_resource_type_idx on tag_link (resource_id, resource_type);
