-- What a hybrid was crossed from, as a relation of its own.
--
-- taxon.parent_id is containment: Acer × freemanii is a nothospecies in the
-- genus Acer, so its parent is Acer, exactly as a plain species' would be.
-- That stays single and stays right. The cross is a different relation --
-- Acer × freemanii is Acer rubrum × Acer saccharinum -- and until now lived
-- only inside the name, where nothing could query it.
--
-- A table rather than two columns on taxon, because a nothogenus can name
-- more than two: × Potinara is Brassavola × Cattleya × Laelia × Sophronitis.
-- The WFO reference loaded here carries 423 nothogenera at genus rank.
--
-- role is a check rather than a lookup table, unlike contact_type: a contact
-- type is a vocabulary a garden outgrows, and seed, pollen and unknown are
-- the complete set of answers to which way a cross went. `unknown` is the
-- default because for most garden records the direction is not known, and a
-- row that says so beats a column that invents one.

CREATE TABLE taxon_parentage (
  id integer primary key autoincrement,
  taxon_id integer not null references taxon(id),
  parent_taxon_id integer not null references taxon(id),
  role text not null default 'unknown'
    check(role in ('seed', 'pollen', 'unknown')),
  position integer not null,
  created_by integer references "user"(id),
  created_at text not null default (datetime('now')),
  -- No cross means this, and it would loop a renderer that walks the chain.
  check (parent_taxon_id <> taxon_id)
) strict;

-- Reading a hybrid's parents.
CREATE INDEX taxon_parentage_taxon_id_idx on taxon_parentage (taxon_id);

-- The other direction: what do I hold with Cattleya in its parentage.
CREATE INDEX taxon_parentage_parent_taxon_id_idx
  on taxon_parentage (parent_taxon_id);

-- position orders the formula so it reads back the way it is written. Unique
-- per taxon, so the order is never ambiguous. A backcross is recorded as a
-- cross with the intermediate hybrid rather than by naming a parent twice.
CREATE UNIQUE INDEX taxon_parentage_position_idx
  on taxon_parentage (taxon_id, position);
