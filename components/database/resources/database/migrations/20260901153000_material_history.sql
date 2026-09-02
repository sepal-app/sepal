-- Material history: a material_change log recording every move, quantity
-- change and death, a material_change_reason lookup table, material relaxed
-- so a dead plant (quantity 0) is representable, and material.status widened
-- from a two-value CHECK to a material_status lookup table carrying Bauble
-- 0.8's full vocabulary.
--
-- SQLite cannot alter a CHECK constraint, so material is rebuilt:
-- material_new with the new checks, copy every row across (normalising a
-- dead plant with a positive quantity to 0, the only legal form), drop the
-- old table, rename material_new to material, then recreate the index and
-- trigger the rebuild dropped along with it.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

CREATE TABLE material_status (
  name text primary key
) strict;

-- Bauble 0.8's full acc_status vocabulary, in single words that round-trip
-- through the csk transforms on the spec, exactly like taxon_rank. `unknown`
-- is Bauble's None: no information. BBG's data only ever used the first two.
INSERT INTO material_status (name) VALUES
  ('alive'), ('dead'), ('dormant'), ('transferred'), ('other'), ('unknown');

CREATE TABLE material_change_reason (
  code text primary key,
  label text not null
) strict;

INSERT INTO material_change_reason (code, label) VALUES
  ('dead', 'Dead'),
  ('discarded', 'Discarded'),
  ('discarded_weedy', 'Discarded, weedy'),
  ('lost', 'Lost, whereabouts unknown'),
  ('stolen', 'Stolen'),
  ('winter_kill', 'Winter kill'),
  ('summer_kill', 'Summer kill'),
  ('error_correction', 'Error correction'),
  ('distributed', 'Distributed elsewhere'),
  ('deleted', 'Deleted, year dead unknown'),
  ('did_not_germinate', 'Did not germinate'),
  ('discarded_seedling', 'Discarded seedling'),
  ('given_away', 'Given away'),
  ('transferred', 'Transferred elsewhere'),
  ('other', 'Other');

CREATE TABLE material_new (
  id integer primary key autoincrement,
  code text not null,
  accession_id integer not null references accession(id),
  location_id integer not null references location(id),
  type text not null default 'plant' check(type in ('plant', 'seed', 'vegetative', 'tissue', 'other')),
  status text not null default 'alive' references material_status(name),
  memorial integer not null default 0 check(memorial in (0, 1)),
  quantity integer not null default 1 check(quantity >= 0),
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now')),
  -- A non-current lot cannot hold material: dead, transferred and other
  -- require quantity 0, while alive, dormant and unknown accept any count.
  check(status in ('alive', 'dormant', 'unknown') or quantity = 0)
) strict;

INSERT INTO material_new (id, code, accession_id, location_id, type, status,
                          memorial, quantity, created_at, updated_at)
SELECT id, code, accession_id, location_id, type, status,
       memorial, CASE WHEN status = 'dead' THEN 0 ELSE quantity END,
       created_at, updated_at
FROM material;
DROP TABLE material;

ALTER TABLE material_new RENAME TO material;

CREATE INDEX material_id_idx on material (id);

CREATE TRIGGER trigger_material_updated_at after update on material
begin
  update material set updated_at = datetime('now') where id = NEW.id;
end;

CREATE TABLE material_change (
  id integer primary key autoincrement,
  material_id integer not null references material(id) on delete cascade,
  from_location_id integer references location(id),
  to_location_id integer references location(id),
  quantity integer not null,
  reason text references material_change_reason(code),
  changed_at text not null default (datetime('now')),
  note text,
  created_by integer references "user"(id),
  created_at text not null default (datetime('now'))
) strict;

CREATE INDEX material_change_material_id_idx on material_change (material_id);
CREATE INDEX material_change_changed_at_idx on material_change (changed_at desc);

-- The guard. A bare `pragma foreign_key_check` prints violations and exits 0,
-- so it fails nothing. Feeding its row count through a CHECK does: on a
-- violation this raises, and -bail rolls the wrapping transaction back.
CREATE TEMP TABLE fk_guard (n integer check (n = 0));
INSERT INTO fk_guard SELECT count(*) FROM pragma_foreign_key_check;
DROP TABLE fk_guard;
