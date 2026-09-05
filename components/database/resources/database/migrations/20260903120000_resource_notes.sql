-- Resource notes: one polymorphic note table carrying free text against an
-- accession, a material or a taxon. Modelled on media_link, which established
-- the (resource_id, resource_type) pattern, and on material_change, which
-- established a nullable created_by.
--
-- created_by is nullable on purpose. The 1,643 notes imported from Belize
-- Botanic Gardens' Bauble backup have no author — Bauble's note.user column is
-- empty on every row — and attributing them to whoever runs the import would
-- put a false name on a real record. Null reads as "author unknown".
--
-- resource_type carries no CHECK. The Malli enum in
-- sepal.note.interface.spec is the constraint, matching media_link.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

CREATE TABLE note (
  id integer primary key autoincrement,
  body text not null,
  resource_id integer not null,
  resource_type text not null,
  created_by integer references "user"(id),
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;

CREATE INDEX note_resource_id_resource_type_idx on note (resource_id, resource_type);

CREATE TRIGGER trigger_note_updated_at after update on note
begin
  update note set updated_at = datetime('now') where id = NEW.id;
end;
