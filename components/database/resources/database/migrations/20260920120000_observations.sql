-- Observations: a dated, typed, countable record of what someone saw, against
-- material or a location.
--
-- Two lookup tables rather than CHECK constraints, following taxon_rank and
-- contact_type: SQLite cannot alter a CHECK, so every value a garden added
-- would rebuild the table. Behind a foreign key each one is an INSERT.
--
-- observation_value is keyed on (type, code) and observation carries a
-- composite foreign key onto it, so a phenology row cannot hold a pest value.
-- The database enforces that rather than the form.
--
-- `general` is a type with no values. It carries a plain remark -- what a
-- material note used to be -- and the composite foreign key permits it
-- because SQLite does not enforce a key with a NULL component.
--
-- This also takes notes off material. Counting is the point of an
-- observation and prose cannot be counted, but material is also the one
-- record where a curator would otherwise have two places to look and two
-- places to write. Accession and taxon keep their notes: you do not walk out
-- and look at a piece of paperwork or at a name.
--
-- resource_type carries no CHECK. The Malli enum in
-- sepal.observation.interface.spec is the constraint, matching note and
-- media_link.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

CREATE TABLE observation_type (
  code text primary key,
  label text not null
) strict;

INSERT INTO observation_type (code, label) VALUES
  ('phenology', 'Phenology'),
  ('condition', 'Condition'),
  ('pest', 'Pest'),
  ('disease', 'Disease'),
  ('general', 'General');

CREATE TABLE observation_value (
  type text not null references observation_type(code),
  code text not null,
  label text not null,
  primary key (type, code)
) strict;

INSERT INTO observation_value (type, code, label) VALUES
  ('phenology', 'vegetative', 'Vegetative'),
  ('phenology', 'budding', 'Budding'),
  ('phenology', 'flowering', 'Flowering'),
  ('phenology', 'fruiting', 'Fruiting'),
  ('phenology', 'seed_dispersal', 'Seed dispersal'),
  ('phenology', 'senescing', 'Senescing'),
  ('phenology', 'dormant', 'Dormant'),
  ('condition', 'excellent', 'Excellent'),
  ('condition', 'good', 'Good'),
  ('condition', 'fair', 'Fair'),
  ('condition', 'poor', 'Poor'),
  ('condition', 'dying', 'Dying'),
  ('condition', 'dead', 'Dead'),
  ('pest', 'none', 'None'),
  ('pest', 'light', 'Light'),
  ('pest', 'moderate', 'Moderate'),
  ('pest', 'severe', 'Severe'),
  ('disease', 'none', 'None'),
  ('disease', 'light', 'Light'),
  ('disease', 'moderate', 'Moderate'),
  ('disease', 'severe', 'Severe');

-- created_by is nullable, matching note and material_change: the rows moved
-- from note include imports that never had an author, and naming whoever ran
-- the import would put a false name on a real record.
--
-- observed_by is free text and separate from created_by. A volunteer walks
-- the beds on Sunday and somebody types it on Tuesday, and that volunteer
-- usually has no user account.
CREATE TABLE observation (
  id integer primary key autoincrement,
  resource_id integer not null,
  resource_type text not null,
  type text not null references observation_type(code),
  value text,
  observed_on text not null,
  observed_by text,
  next_check_on text,
  note text,
  created_by integer references "user"(id),
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now')),
  foreign key (type, value) references observation_value(type, code)
) strict;

CREATE INDEX observation_resource_id_resource_type_idx
  on observation (resource_id, resource_type);
CREATE INDEX observation_type_observed_on_idx on observation (type, observed_on);
CREATE INDEX observation_next_check_on_idx on observation (next_check_on);

CREATE TRIGGER trigger_observation_updated_at after update on observation
begin
  update observation set updated_at = datetime('now') where id = NEW.id;
end;

-- Material notes become general observations.
--
-- date(created_at) is the only date these rows carry. For a note that arrived
-- through the importer it is the import date rather than the day anyone
-- looked at the plant. That is acceptable here and would not be against a
-- real garden -- Sepal has no users yet, so these rows are development data.
-- bauble2sepal emits Bauble's own note date instead, which is better.
INSERT INTO observation (resource_id, resource_type, type, value, observed_on,
                         observed_by, next_check_on, note, created_by,
                         created_at, updated_at)
SELECT resource_id, 'material', 'general', NULL, date(created_at),
       NULL, NULL, body, created_by, created_at, updated_at
FROM note
WHERE resource_type = 'material';

DELETE FROM note WHERE resource_type = 'material';

-- The guard. A bare `pragma foreign_key_check` prints violations and exits 0,
-- so it fails nothing. Feeding its row count through a CHECK does: on a
-- violation this raises, and -bail rolls the wrapping transaction back.
CREATE TEMP TABLE fk_guard (n integer check (n = 0));
INSERT INTO fk_guard SELECT count(*) FROM pragma_foreign_key_check;
DROP TABLE fk_guard;
