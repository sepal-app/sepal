-- Propagation: the record that a garden grew material itself.
--
-- Two lookup tables rather than CHECK constraints, following material_status
-- and accession_received_type: SQLite cannot alter a CHECK, so a seventh
-- method would rebuild the table. Behind a foreign key it is an INSERT.
--
-- propagation_type.clonal says whether the method yields the parent genotype.
-- It lives on the lookup row because it is a property of the method, so a
-- garden adding a type has to say which kind it is. It is nullable: `other`
-- does not decide. Nothing in the database reads it -- it is one input to the
-- product the form defaults to, and the parent's material type is the other.
--
-- parent_accession_id is not null. The accession is the durable anchor:
-- material can be deleted while the accession persists, and deleting a plant
-- must not orphan the lineage of everything grown from it.
-- parent_material_id narrows it to the individual when that is known, which
-- is not always. A parent outside the collection is not a propagation -- that
-- material arrives as an accession with a supplier contact, and this table
-- does not duplicate that path.
--
-- rootstock_taxon_id is a graft's other parent, identified by cultivar rather
-- than by a plant here: commercial rootstock is bought by the bundle and
-- almost never accessioned, so an accession reference would be null in
-- exactly the case a fruit collection cares about.
--
-- Both product columns are nullable and neither is tied to the method. A
-- clone reaccessioned for a research project is legitimate, and so is a batch
-- that produced both -- ten cuttings staying under the parent accession and
-- two reaccessioned. A propagation that produced nothing is also a real
-- record: it is how a garden learns a taxon is hard to strike.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

CREATE TABLE propagation_type (
  name text primary key,
  label text not null,
  -- 1 when the method yields the parent genotype, 0 when it does not, null
  -- when the method does not decide.
  clonal integer check(clonal in (0, 1))
) strict;

INSERT INTO propagation_type (name, label, clonal) VALUES
  ('seed',           'Seed',           0),
  ('cutting',        'Cutting',        1),
  ('division',       'Division',       1),
  ('graft',          'Graft',          1),
  ('layering',       'Layering',       1),
  ('tissue_culture', 'Tissue culture', 1),
  ('other',          'Other',          null);

CREATE TABLE propagation_status (
  name text primary key,
  label text not null
) strict;

-- Three values, not a pipeline. `active` is the nursery worklist; the other
-- two take a batch off it. Whether products exist already implies the
-- difference, but a propagator has to be able to close a batch out in one
-- click rather than by waiting for someone to create records.
INSERT INTO propagation_status (name, label) VALUES
  ('active',   'In progress'),
  ('complete', 'Complete'),
  ('failed',   'Failed');

CREATE TABLE propagation (
  id integer primary key autoincrement,
  type text not null references propagation_type(name),
  status text not null default 'active' references propagation_status(name),
  parent_accession_id integer not null references accession(id),
  parent_material_id integer references material(id),
  rootstock_taxon_id integer references taxon(id),
  -- Where the batch physically sits while it runs. A nursery bench is a
  -- location like any other, and archiving handles one that goes away.
  location_id integer references location(id),
  propagated_on text,
  succeeded_on text,
  -- Propagules started, and how many came through: germinated for seed,
  -- struck for cuttings, taken for grafts. Null means uncounted, which is the
  -- normal case for a mass sowing -- and recording a mass sowing as null
  -- rather than as an estimate is what makes the comparison below safe.
  -- Either side being null passes it.
  quantity_started integer check(quantity_started >= 0),
  quantity_succeeded integer check(quantity_succeeded >= 0)
    check(quantity_succeeded <= quantity_started),
  created_by integer references "user"(id),
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;

CREATE INDEX propagation_parent_accession_id_idx
  on propagation (parent_accession_id);
CREATE INDEX propagation_parent_material_id_idx
  on propagation (parent_material_id);
CREATE INDEX propagation_rootstock_taxon_id_idx
  on propagation (rootstock_taxon_id);
CREATE INDEX propagation_location_id_idx on propagation (location_id);
-- The nursery list asks for active propagations by default.
CREATE INDEX propagation_status_idx on propagation (status);

CREATE TRIGGER trigger_propagation_updated_at after update on propagation
begin
  update propagation set updated_at = datetime('now') where id = NEW.id;
end;

-- parent_material_id, when given, must belong to parent_accession_id. SQLite
-- cannot express that as a CHECK because it needs a subquery, and an
-- invariant this cheap belongs in the database rather than in the form.
CREATE TRIGGER trigger_propagation_parent_material_insert
before insert on propagation
when NEW.parent_material_id is not null
begin
  select raise(abort, 'parent_material_id must belong to parent_accession_id')
  where not exists (
    select 1 from material
    where id = NEW.parent_material_id
      and accession_id = NEW.parent_accession_id
  );
end;

CREATE TRIGGER trigger_propagation_parent_material_update
before update on propagation
when NEW.parent_material_id is not null
begin
  select raise(abort, 'parent_material_id must belong to parent_accession_id')
  where not exists (
    select 1 from material
    where id = NEW.parent_material_id
      and accession_id = NEW.parent_accession_id
  );
end;

-- The products. Both default to null, so neither costs a table rebuild and
-- accession_fts and its triggers stay untouched -- the same reason the
-- receipt migration adds received_type the way it does.
--
-- material.propagation_id is the clonal case and the commonest one: a cutting
-- from accession 2026.0042 material 1 becomes material 2 of the same
-- accession, which is what a qualifier has meant in plant records since 1874.
-- accession.propagation_id is a new genotype -- seed off a growing plant.
ALTER TABLE material ADD COLUMN propagation_id integer references propagation(id);
ALTER TABLE accession ADD COLUMN propagation_id integer references propagation(id);

CREATE INDEX material_propagation_id_idx on material (propagation_id);
CREATE INDEX accession_propagation_id_idx on accession (propagation_id);

-- A division is the only method of the six that consumes anything, and the
-- history log had no word for it: it has `distributed`, `transferred` and
-- `given_away`, but nothing for a plant that was split.
INSERT INTO material_change_reason (code, label) VALUES ('divided', 'Divided');

-- The guard. A bare `pragma foreign_key_check` prints violations and exits 0,
-- so it fails nothing. Feeding its row count through a CHECK does: on a
-- violation this raises, and -bail rolls the wrapping transaction back.
CREATE TEMP TABLE fk_guard (n integer check (n = 0));
INSERT INTO fk_guard SELECT count(*) FROM pragma_foreign_key_check;
DROP TABLE fk_guard;
